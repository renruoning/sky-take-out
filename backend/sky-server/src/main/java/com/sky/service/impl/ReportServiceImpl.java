package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final OrderDetailMapper orderDetailMapper;

    ReportServiceImpl(OrderMapper orderMapper, UserMapper userMapper, OrderDetailMapper orderDetailMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.orderDetailMapper = orderDetailMapper;
    }

    /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        // 计算日期区间内的每一天
        List<LocalDate> dateList = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);
        }

        // 查询每一天的营业额（已完成订单的实收金额之和）
        List<Double> turnoverList = dateList.stream()
                .map(this::getTurnover)
                .collect(Collectors.toList());

        return TurnoverReportVO.builder()
                .dateList(dateList.stream().map(LocalDate::toString).collect(Collectors.joining(",")))
                .turnoverList(turnoverList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .build();
    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);
        }

        List<Integer> totalUserList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            // 总用户数：截至当天24点前注册的用户总数
            Map<String, Object> totalMap = new HashMap<>();
            totalMap.put("end", endTime);
            totalUserList.add(getUserCount(totalMap));

            // 新增用户数：当天注册的用户数
            Map<String, Object> newMap = new HashMap<>();
            newMap.put("begin", LocalDateTime.of(date, LocalTime.MIN));
            newMap.put("end", endTime);
            newUserList.add(getUserCount(newMap));
        }

        return UserReportVO.builder()
                .dateList(dateList.stream().map(LocalDate::toString).collect(Collectors.joining(",")))
                .totalUserList(totalUserList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .newUserList(newUserList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .build();
    }

    /**
     * 根据条件统计用户数量
     * @param map
     * @return
     */
    private Integer getUserCount(Map<String, Object> map) {
        Integer count = userMapper.countByMap(map);
        return count == null ? 0 : count;
    }

    /**
     * 订单统计
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO ordersStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);
        }

        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            orderCountList.add(getOrderCount(beginTime, endTime, null));
            validOrderCountList.add(getOrderCount(beginTime, endTime, Orders.COMPLETED));
        }

        // 时间区间内的订单总数、有效订单总数
        Integer totalOrderCount = orderCountList.stream().mapToInt(Integer::intValue).sum();
        Integer validOrderCount = validOrderCountList.stream().mapToInt(Integer::intValue).sum();

        // 订单完成率
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }

        return OrderReportVO.builder()
                .dateList(dateList.stream().map(LocalDate::toString).collect(Collectors.joining(",")))
                .orderCountList(orderCountList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .validOrderCountList(validOrderCountList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 统计指定时间区间内的订单数量
     * @param begin
     * @param end
     * @param status 为空表示不限制订单状态
     * @return
     */
    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {
        Map<String, Object> map = new HashMap<>();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);

        Integer count = orderMapper.countByMap(map);
        return count == null ? 0 : count;
    }

    /**
     * 销量排名top10统计
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO salesTop10Statistics(LocalDate begin, LocalDate end) {
        List<GoodsSalesDTO> salesTop10 = orderDetailMapper.getSalesTop10(
                Orders.COMPLETED,
                LocalDateTime.of(begin, LocalTime.MIN),
                LocalDateTime.of(end, LocalTime.MAX));

        List<String> nameList = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numberList = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());

        return SalesTop10ReportVO.builder()
                .nameList(String.join(",", nameList))
                .numberList(numberList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .build();
    }

    /**
     * 查询某一天的营业额
     * @param date
     * @return
     */
    private Double getTurnover(LocalDate date) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", Orders.COMPLETED);
        map.put("begin", LocalDateTime.of(date, LocalTime.MIN));
        map.put("end", LocalDateTime.of(date, LocalTime.MAX));

        Double turnover = orderMapper.sumByMap(map);
        return turnover == null ? 0.0 : turnover;
    }

    /**
     * 导出最近30天营业额数据Excel报表
     * @param response
     */
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        List<LocalDate> dateList = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);
        }
        List<Double> turnoverList = dateList.stream()
                .map(this::getTurnover)
                .collect(Collectors.toList());

        try (XSSFWorkbook excel = new XSSFWorkbook()) {
            Sheet sheet = excel.createSheet("营业额统计报表");

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("营业额统计报表：" + begin + " 至 " + end);

            Row headRow = sheet.createRow(1);
            headRow.createCell(0).setCellValue("日期");
            headRow.createCell(1).setCellValue("营业额");

            for (int i = 0; i < dateList.size(); i++) {
                Row row = sheet.createRow(i + 2);
                row.createCell(0).setCellValue(dateList.get(i).toString());
                row.createCell(1).setCellValue(turnoverList.get(i));
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=turnoverStatistics.xlsx");

            try (OutputStream out = response.getOutputStream()) {
                excel.write(out);
            }
        } catch (IOException e) {
            log.error("导出营业额报表失败：{}", e.getMessage());
        }
    }
}
