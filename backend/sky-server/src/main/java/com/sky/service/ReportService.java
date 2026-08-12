package com.sky.service;

import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {

    /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end);

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    UserReportVO userStatistics(LocalDate begin, LocalDate end);

    /**
     * 订单统计
     * @param begin
     * @param end
     * @return
     */
    OrderReportVO ordersStatistics(LocalDate begin, LocalDate end);

    /**
     * 销量排名top10统计
     * @param begin
     * @param end
     * @return
     */
    SalesTop10ReportVO salesTop10Statistics(LocalDate begin, LocalDate end);

    /**
     * 导出最近30天营业额数据Excel报表
     * @param response
     */
    void exportBusinessData(HttpServletResponse response);

    /**
     * 导出最近30天运营数据Excel报表
     * @param response
     */
    void exportOperatingData(HttpServletResponse response);
}
