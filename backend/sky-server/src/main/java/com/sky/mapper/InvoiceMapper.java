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

    @Insert("insert into invoice (order_id, user_id, shop_id, title, invoice_type, tax_number, email, amount, create_time) " +
            "values (#{orderId}, #{userId}, #{shopId}, #{title}, #{invoiceType}, #{taxNumber}, #{email}, #{amount}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Invoice invoice);

    @Select("select * from invoice where order_id = #{orderId}")
    Invoice getByOrderId(Long orderId);

    @Select("select i.*, o.number as orderNumber, o.order_time as orderTime, s.name as shopName " +
            "from invoice i " +
            "left join orders o on o.id = i.order_id " +
            "left join shop s on s.id = i.shop_id " +
            "where i.id = #{id}")
    InvoiceVO getVOById(Long id);

    @Select("select i.*, o.number as orderNumber, o.order_time as orderTime, s.name as shopName " +
            "from invoice i " +
            "left join orders o on o.id = i.order_id " +
            "left join shop s on s.id = i.shop_id " +
            "where i.user_id = #{userId} order by i.create_time desc")
    List<InvoiceVO> listByUserId(@Param("userId") Long userId);
}
