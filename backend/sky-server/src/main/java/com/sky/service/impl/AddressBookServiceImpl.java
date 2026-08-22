package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.properties.SecurityProperties;
import com.sky.service.AddressBookService;
import com.sky.utils.FieldCryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {

    private final AddressBookMapper addressBookMapper;
    private final SecurityProperties securityProperties;

    AddressBookServiceImpl(AddressBookMapper addressBookMapper, SecurityProperties securityProperties) {
        this.addressBookMapper = addressBookMapper;
        this.securityProperties = securityProperties;
    }

    private void decryptPhone(AddressBook addressBook) {
        if (addressBook == null) {
            return;
        }
        addressBook.setPhone(FieldCryptoUtil.decrypt(addressBook.getPhone(), securityProperties.getFieldEncryptionKey()));
    }

    /**
     * 条件查询。注意：phone这个查询条件字段现在是加密存储的，如果哪天真的要按明文手机号查地址簿，
     * 这里传进来的phone得先加密成密文才能匹配上（目前没有任何调用方会设置phone这个查询条件，是个死代码分支）
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        List<AddressBook> list = addressBookMapper.list(addressBook);
        list.forEach(this::decryptPhone);
        return list;
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    public void save(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBook.setPhone(FieldCryptoUtil.encrypt(addressBook.getPhone(), securityProperties.getFieldEncryptionKey()));
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询。cache-aside：地址簿低频写、命中率天然接近100%，但在结算这个高并发入口是必经环节
     * （下单提交时要按addressBookId查一次收货信息），缓存收益不小。
     *
     * @param id
     * @return
     */
    @Cacheable(cacheNames = "addressBookCache", key = "#id")
    public AddressBook getById(Long id) {
        AddressBook addressBook = addressBookMapper.getById(id);
        decryptPhone(addressBook);
        return addressBook;
    }

    /**
     * 根据id修改地址
     *
     * @param addressBook
     */
    @CacheEvict(cacheNames = "addressBookCache", key = "#addressBook.id")
    public void update(AddressBook addressBook) {
        if (addressBook.getPhone() != null) {
            addressBook.setPhone(FieldCryptoUtil.encrypt(addressBook.getPhone(), securityProperties.getFieldEncryptionKey()));
        }
        addressBookMapper.update(addressBook);
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Transactional
    // 这个操作会把当前用户名下所有地址的is_default都改一遍（先全部清0，再把目标地址设为1），
    // 不是只改了addressBook.id这一条，没法用单个key精确清缓存，直接清空整个cache更安全（地址簿本来写就很少，代价可以接受）
    @CacheEvict(cacheNames = "addressBookCache", allEntries = true)
    public void setDefault(AddressBook addressBook) {
        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2、将当前地址改为默认地址 update address_book set is_default = ? where id = ?
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    /**
     * 根据id删除地址
     *
     * @param id
     */
    @CacheEvict(cacheNames = "addressBookCache", key = "#id")
    public void deleteById(Long id) {
        addressBookMapper.deleteById(id);
    }

}
