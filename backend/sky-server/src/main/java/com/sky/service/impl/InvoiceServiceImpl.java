package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.InvoiceApplyDTO;
import com.sky.entity.Invoice;
import com.sky.entity.Orders;
import com.sky.exception.InvoiceBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.InvoiceMapper;
import com.sky.mapper.OrderMapper;
import com.sky.service.InvoiceService;
import com.sky.vo.InvoiceVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceMapper invoiceMapper;
    private final OrderMapper orderMapper;

    InvoiceServiceImpl(InvoiceMapper invoiceMapper, OrderMapper orderMapper) {
        this.invoiceMapper = invoiceMapper;
        this.orderMapper = orderMapper;
    }

    public InvoiceVO apply(InvoiceApplyDTO invoiceApplyDTO) {
        Long userId = BaseContext.getCurrentId();

        Orders order = orderMapper.getById(invoiceApplyDTO.getOrderId());
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new InvoiceBusinessException(MessageConstant.INVOICE_ORDER_NOT_OWNED);
        }
        if (!order.getPayStatus().equals(Orders.PAID)) {
            throw new InvoiceBusinessException(MessageConstant.INVOICE_ORDER_NOT_PAID);
        }
        if (invoiceMapper.getByOrderId(order.getId()) != null) {
            throw new InvoiceBusinessException(MessageConstant.INVOICE_ALREADY_EXISTS);
        }
        if (Invoice.TYPE_COMPANY.equals(invoiceApplyDTO.getInvoiceType())
                && (invoiceApplyDTO.getTaxNumber() == null || invoiceApplyDTO.getTaxNumber().trim().isEmpty())) {
            throw new InvoiceBusinessException(MessageConstant.INVOICE_TAX_NUMBER_REQUIRED);
        }

        Invoice invoice = Invoice.builder()
                .orderId(order.getId())
                .userId(userId)
                .shopId(order.getShopId())
                .title(invoiceApplyDTO.getTitle())
                .invoiceType(invoiceApplyDTO.getInvoiceType())
                .taxNumber(invoiceApplyDTO.getTaxNumber())
                .email(invoiceApplyDTO.getEmail())
                .amount(order.getAmount())
                .createTime(LocalDateTime.now())
                .build();
        invoiceMapper.insert(invoice);

        return invoiceMapper.getVOById(invoice.getId());
    }

    public List<InvoiceVO> listByUser() {
        return invoiceMapper.listByUserId(BaseContext.getCurrentId());
    }

    public InvoiceVO getById(Long id) {
        InvoiceVO invoiceVO = invoiceMapper.getVOById(id);
        if (invoiceVO == null || !invoiceVO.getUserId().equals(BaseContext.getCurrentId())) {
            throw new InvoiceBusinessException(MessageConstant.INVOICE_NOT_FOUND);
        }
        return invoiceVO;
    }
}
