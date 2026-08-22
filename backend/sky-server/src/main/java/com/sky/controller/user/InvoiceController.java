package com.sky.controller.user;

import com.sky.dto.InvoiceApplyDTO;
import com.sky.result.Result;
import com.sky.service.InvoiceService;
import com.sky.vo.InvoiceVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/invoice")
@Slf4j
@Api(tags = "C端-发票相关接口")
public class InvoiceController {

    private final InvoiceService invoiceService;

    InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * 申请发票
     * @param invoiceApplyDTO
     * @return
     */
    @PostMapping("/apply")
    @ApiOperation("申请发票")
    public Result<InvoiceVO> apply(@RequestBody InvoiceApplyDTO invoiceApplyDTO) {
        log.info("申请发票: {}", invoiceApplyDTO);
        return Result.success(invoiceService.apply(invoiceApplyDTO));
    }

    /**
     * 查询当前用户的发票列表
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("查询发票列表")
    public Result<List<InvoiceVO>> list() {
        return Result.success(invoiceService.listByUser());
    }

    /**
     * 查询发票详情（用于打印/查看）
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("查询发票详情")
    public Result<InvoiceVO> getById(@PathVariable Long id) {
        return Result.success(invoiceService.getById(id));
    }
}
