package com.sky.service;

import com.sky.dto.InvoiceApplyDTO;
import com.sky.vo.InvoiceVO;

import java.util.List;

public interface InvoiceService {

    /**
     * 申请发票
     * @param invoiceApplyDTO
     * @return
     */
    InvoiceVO apply(InvoiceApplyDTO invoiceApplyDTO);

    /**
     * 查询当前用户的发票列表
     * @return
     */
    List<InvoiceVO> listByUser();

    /**
     * 查询发票详情（校验归属当前用户）
     * @param id
     * @return
     */
    InvoiceVO getById(Long id);
}
