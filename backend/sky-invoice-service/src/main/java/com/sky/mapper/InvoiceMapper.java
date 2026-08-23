package com.sky.mapper;

import com.sky.entity.Invoice;
import com.sky.vo.InvoiceVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvoiceMapper {

    @Insert("insert into invoice (order_id, user_id, shop_id, title, invoice_type, tax_number, email, amount, " +
            "order_number, order_time, shop_name, create_time) " +
            "values (#{orderId}, #{userId}, #{shopId}, #{title}, #{invoiceType}, #{taxNumber}, #{email}, #{amount}, " +
            "#{orderNumber}, #{orderTime}, #{shopName}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Invoice invoice);

    @Select("select * from invoice where order_id = #{orderId}")
    Invoice getByOrderId(Long orderId);

    // order_number/order_time/shop_name是申请时从sky-server快照下来的自己的列，不再JOIN别的表——
    // 发票库物理拆开之后orders/shop表根本不在这个库里，也没必要再连回去查
    @Select("select id, order_id as orderId, user_id as userId, order_number as orderNumber, shop_name as shopName, " +
            "title, invoice_type as invoiceType, tax_number as taxNumber, email, amount, order_time as orderTime, create_time as createTime " +
            "from invoice where id = #{id}")
    InvoiceVO getVOById(Long id);

    @Select("select id, order_id as orderId, user_id as userId, order_number as orderNumber, shop_name as shopName, " +
            "title, invoice_type as invoiceType, tax_number as taxNumber, email, amount, order_time as orderTime, create_time as createTime " +
            "from invoice where user_id = #{userId} order by create_time desc")
    List<InvoiceVO> listByUserId(@Param("userId") Long userId);
}
