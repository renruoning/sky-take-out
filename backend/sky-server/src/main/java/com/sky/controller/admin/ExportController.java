package com.sky.controller.admin;

import com.sky.service.ReportService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/**
 * 报表导出
 */
@RestController
@RequestMapping("/admin/export")
@Slf4j
@Api(tags = "报表导出接口")
public class ExportController {

    private final ReportService reportService;

    ExportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 导出最近30天运营数据Excel报表
     * @param response
     */
    @GetMapping("/export")
    @ApiOperation("导出最近30天运营数据Excel报表")
    public void export(HttpServletResponse response) {
        reportService.exportOperatingData(response);
    }
}
