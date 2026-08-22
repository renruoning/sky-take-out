package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户申请发票
 */
@Data
public class InvoiceApplyDTO implements Serializable {

    private Long orderId;

    //发票抬头
    private String title;

    //抬头类型 1个人 2单位
    private Integer invoiceType;

    //纳税人识别号，单位抬头必填
    private String taxNumber;

    //接收邮箱，可选
    private String email;
}
