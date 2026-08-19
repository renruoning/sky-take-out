package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.EmployeeDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.exception.ShopBusinessException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    EmployeeServiceImpl(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        // 1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        // 2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            // 账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 密码比对
        // 对前端传过来的明文密码进行MD5加密
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(employee.getPassword())) {
            // 密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            // 账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }
        // 3、返回实体对象
        return employee;
    }

    /**
     * 新增员工
     *
     * @param employeeDTO
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        System.out.println("当前线程的id：" + Thread.currentThread().getId());
        // 创建实体对象，将来要保存到数据库的就是这个实体对象
        Employee employee = new Employee();
        // 拷贝属性
        BeanUtils.copyProperties(employeeDTO, employee);
        // 设置状态,默认正常状态是1，是一个常量
        employee.setStatus(StatusConstant.ENABLE);
        // 设置初始密码，123456，进行MD5加密
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));

        // 店铺归属：店铺员工新增下属时强制归属自己所在店铺；平台超管新增员工必须显式指定目标店铺
        Long currentShopId = BaseContext.getCurrentShopId();
        if (currentShopId != null) {
            employee.setShopId(currentShopId);
        } else if (employeeDTO.getShopId() != null) {
            employee.setShopId(employeeDTO.getShopId());
        } else {
            throw new ShopBusinessException(MessageConstant.PLATFORM_MUST_SPECIFY_SHOP);
        }

        employeeMapper.insert(employee);
    }

    /**
     * 员工分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        // 实现分页查询逻辑
        employeePageQueryDTO.setShopId(BaseContext.getCurrentShopId());
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        long total = page.getTotal(); // 获取总记录数
        List<Employee> records = page.getResult(); // 获取当前页的记录列表
        return new PageResult(total, records); // 返回分页结果
    }

    /**
     * 根据员工id启用或禁用员工账号
     *
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setStatus(status);
        employee.setShopId(BaseContext.getCurrentShopId());
        employeeMapper.update(employee);
    }

    /**
     * 根据id查询员工信息（店铺员工只能查看本店员工，平台超管可查看所有）
     *
     * @param id
     * @return
     */
    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        if (employee == null) {
            return null;
        }
        Long currentShopId = BaseContext.getCurrentShopId();
        if (currentShopId != null && !currentShopId.equals(employee.getShopId())) {
            return null;
        }
        employee.setPassword("****"); // 不返回密码信息
        return employee;
    }

    /**
     * 编辑员工信息
     *
     * @param employeeDTO
     */
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employee.setShopId(BaseContext.getCurrentShopId());
        employeeMapper.update(employee);
    }
}
