package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private Long userId;

    private String orderNumber;

    private String shopName;

    private String title;

    private Integer invoiceType;

    private String taxNumber;

    private String email;

    private BigDecimal amount;

    private LocalDateTime orderTime;

    private LocalDateTime createTime;
}
