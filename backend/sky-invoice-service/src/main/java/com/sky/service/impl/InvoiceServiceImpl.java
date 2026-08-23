package com.sky.service.impl;

import com.sky.client.OrderClient;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.InvoiceApplyDTO;
import com.sky.entity.Invoice;
import com.sky.exception.InvoiceBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.InvoiceMapper;
import com.sky.result.Result;
import com.sky.service.InvoiceService;
import com.sky.vo.InvoiceVO;
import com.sky.vo.OrderSummaryVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceMapper invoiceMapper;
    private final OrderClient orderClient;

    InvoiceServiceImpl(InvoiceMapper invoiceMapper, OrderClient orderClient) {
        this.invoiceMapper = invoiceMapper;
        this.orderClient = orderClient;
    }

    public InvoiceVO apply(InvoiceApplyDTO invoiceApplyDTO) {
        Long userId = BaseContext.getCurrentId();

        // 发票服务自己没有订单表，订单是否存在/归属谁/是否已支付都得问sky-server要（唯一一次强一致读，
        // 用同步RPC而不是本地缓存的订单副本——发票申请这个动作本身要求校验的是"此时此刻"的真实支付状态）
        Result<OrderSummaryVO> orderResult = orderClient.getOrderSummary(invoiceApplyDTO.getOrderId());
        OrderSummaryVO order = (orderResult != null && orderResult.getCode() != null && orderResult.getCode() == 1)
                ? orderResult.getData() : null;
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new InvoiceBusinessException(MessageConstant.INVOICE_ORDER_NOT_OWNED);
        }
        // payStatus=1对应sky-server端Orders.PAID这个约定；发票服务没有Orders实体，
        // OrderSummaryVO只是个纯数据传输对象，这里保持跟sky-server一致的整数含义
        if (!order.getPayStatus().equals(1)) {
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
                // 申请这一刻从sky-server快照下来的展示字段，之后即使订单/店铺信息变了，这张发票上看到的还是当时的样子
                .orderNumber(order.getNumber())
                .orderTime(order.getOrderTime())
                .shopName(order.getShopName())
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
