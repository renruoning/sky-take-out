package com.sky.service.impl;

import com.sky.client.OrderClient;
import com.sky.context.BaseContext;
import com.sky.dto.DailyOrderStatDTO;
import com.sky.dto.DailyUserStatDTO;
import com.sky.dto.GoodsSalesDTO;
import com.sky.mapper.UserMapper;
import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
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

    private final UserMapper userMapper;
    private final OrderClient orderClient;
    private final WorkspaceService workspaceService;

    ReportServiceImpl(UserMapper userMapper, OrderClient orderClient, WorkspaceService workspaceService) {
        this.userMapper = userMapper;
        this.orderClient = orderClient;
        this.workspaceService = workspaceService;
    }

    /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = buildDateList(begin, end);
        Map<LocalDate, DailyOrderStatDTO> orderStatMap = getDailyOrderStatMap(begin, end);

        List<Double> turnoverList = dateList.stream()
                .map(date -> {
                    DailyOrderStatDTO stat = orderStatMap.get(date);
                    return stat == null ? 0.0 : stat.getTurnover();
                })
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
        List<LocalDate> dateList = buildDateList(begin, end);

        // 截至begin当天开始前（不含）已注册的用户数，作为累计总数的起点
        Map<String, Object> beforeMap = new HashMap<>();
        beforeMap.put("end", LocalDateTime.of(begin, LocalTime.MIN).minusNanos(1));
        int runningTotal = getUserCount(beforeMap);

        Map<LocalDate, Integer> newUserMap = userMapper.countGroupByDate(
                        LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX)).stream()
                .collect(Collectors.toMap(DailyUserStatDTO::getDate, DailyUserStatDTO::getNewUserCount));

        List<Integer> totalUserList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        for (LocalDate date : dateList) {
            int newCount = newUserMap.getOrDefault(date, 0);
            runningTotal += newCount;
            newUserList.add(newCount);
            totalUserList.add(runningTotal);
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
        List<LocalDate> dateList = buildDateList(begin, end);
        Map<LocalDate, DailyOrderStatDTO> orderStatMap = getDailyOrderStatMap(begin, end);

        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            DailyOrderStatDTO stat = orderStatMap.get(date);
            orderCountList.add(stat == null ? 0 : stat.getTotalOrderCount());
            validOrderCountList.add(stat == null ? 0 : stat.getValidOrderCount());
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
     * 销量排名top10统计
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO salesTop10Statistics(LocalDate begin, LocalDate end) {
        Result<List<GoodsSalesDTO>> result = orderClient.getSalesTop10(BaseContext.getCurrentShopId(), begin, end);
        List<GoodsSalesDTO> salesTop10 = (result != null && result.getCode() != null && result.getCode() == 1 && result.getData() != null)
                ? result.getData() : new ArrayList<>();

        List<String> nameList = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numberList = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());

        return SalesTop10ReportVO.builder()
                .nameList(String.join(",", nameList))
                .numberList(numberList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .build();
    }

    /**
     * 导出最近30天营业额数据Excel报表
     * @param response
     */
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        List<LocalDate> dateList = buildDateList(begin, end);
        Map<LocalDate, DailyOrderStatDTO> orderStatMap = getDailyOrderStatMap(begin, end);
        List<Double> turnoverList = dateList.stream()
                .map(date -> {
                    DailyOrderStatDTO stat = orderStatMap.get(date);
                    return stat == null ? 0.0 : stat.getTurnover();
                })
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

    /**
     * 导出最近30天运营数据Excel报表
     * @param response
     */
    public void exportOperatingData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        // 概览数据：整个时间区间（单次范围聚合，不按天循环）
        BusinessDataVO businessData = workspaceService.getBusinessData(
                LocalDateTime.of(begin, LocalTime.MIN),
                LocalDateTime.of(end, LocalTime.MAX));

        List<LocalDate> dateList = buildDateList(begin, end);
        Map<LocalDate, DailyOrderStatDTO> orderStatMap = getDailyOrderStatMap(begin, end);
        Map<LocalDate, Integer> newUserMap = userMapper.countGroupByDate(
                        LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX)).stream()
                .collect(Collectors.toMap(DailyUserStatDTO::getDate, DailyUserStatDTO::getNewUserCount));

        // 每日运营数据：由已按天聚合好的查询结果在内存中拼装，不再逐天查库
        List<BusinessDataVO> dailyDataList = new ArrayList<>();
        for (LocalDate date : dateList) {
            DailyOrderStatDTO stat = orderStatMap.get(date);
            int totalOrderCount = stat == null ? 0 : stat.getTotalOrderCount();
            int validOrderCount = stat == null ? 0 : stat.getValidOrderCount();
            double turnover = stat == null ? 0.0 : stat.getTurnover();

            double orderCompletionRate = totalOrderCount == 0 ? 0.0 : (double) validOrderCount / totalOrderCount;
            double unitPrice = validOrderCount == 0 ? 0.0 : turnover / validOrderCount;
            int newUsers = newUserMap.getOrDefault(date, 0);

            dailyDataList.add(BusinessDataVO.builder()
                    .turnover(turnover)
                    .validOrderCount(validOrderCount)
                    .orderCompletionRate(orderCompletionRate)
                    .unitPrice(unitPrice)
                    .newUsers(newUsers)
                    .build());
        }

        try (XSSFWorkbook excel = new XSSFWorkbook()) {
            Sheet sheet = excel.createSheet("运营数据统计报表");

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("运营数据统计报表：" + begin + " 至 " + end);

            Row overviewHeadRow = sheet.createRow(1);
            overviewHeadRow.createCell(0).setCellValue("营业额");
            overviewHeadRow.createCell(1).setCellValue("有效订单数");
            overviewHeadRow.createCell(2).setCellValue("订单完成率");
            overviewHeadRow.createCell(3).setCellValue("平均客单价");
            overviewHeadRow.createCell(4).setCellValue("新增用户数");

            Row overviewDataRow = sheet.createRow(2);
            overviewDataRow.createCell(0).setCellValue(businessData.getTurnover());
            overviewDataRow.createCell(1).setCellValue(businessData.getValidOrderCount());
            overviewDataRow.createCell(2).setCellValue(businessData.getOrderCompletionRate());
            overviewDataRow.createCell(3).setCellValue(businessData.getUnitPrice());
            overviewDataRow.createCell(4).setCellValue(businessData.getNewUsers());

            Row detailHeadRow = sheet.createRow(4);
            detailHeadRow.createCell(0).setCellValue("日期");
            detailHeadRow.createCell(1).setCellValue("营业额");
            detailHeadRow.createCell(2).setCellValue("有效订单数");
            detailHeadRow.createCell(3).setCellValue("订单完成率");
            detailHeadRow.createCell(4).setCellValue("平均客单价");
            detailHeadRow.createCell(5).setCellValue("新增用户数");

            for (int i = 0; i < dateList.size(); i++) {
                BusinessDataVO daily = dailyDataList.get(i);
                Row row = sheet.createRow(i + 5);
                row.createCell(0).setCellValue(dateList.get(i).toString());
                row.createCell(1).setCellValue(daily.getTurnover());
                row.createCell(2).setCellValue(daily.getValidOrderCount());
                row.createCell(3).setCellValue(daily.getOrderCompletionRate());
                row.createCell(4).setCellValue(daily.getUnitPrice());
                row.createCell(5).setCellValue(daily.getNewUsers());
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=operatingData.xlsx");

            try (OutputStream out = response.getOutputStream()) {
                excel.write(out);
            }
        } catch (IOException e) {
            log.error("导出运营数据报表失败：{}", e.getMessage());
        }
    }

    /**
     * 生成 [begin, end] 区间内的日期列表
     */
    private List<LocalDate> buildDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);
        }
        return dateList;
    }

    /**
     * 一次查询获取区间内每天的订单统计（总数/有效数/营业额），代替按天循环查询
     */
    private Map<LocalDate, DailyOrderStatDTO> getDailyOrderStatMap(LocalDate begin, LocalDate end) {
        Result<List<DailyOrderStatDTO>> result = orderClient.getDailyStats(BaseContext.getCurrentShopId(), begin, end);
        List<DailyOrderStatDTO> statList = (result != null && result.getCode() != null && result.getCode() == 1 && result.getData() != null)
                ? result.getData() : new ArrayList<>();
        return statList.stream().collect(Collectors.toMap(DailyOrderStatDTO::getDate, s -> s));
    }
}
