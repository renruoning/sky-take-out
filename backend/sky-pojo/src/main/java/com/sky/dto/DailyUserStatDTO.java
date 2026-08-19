package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 按天分组统计的新增用户数，用于报表按天聚合查询，
 * 避免按天循环单独发SQL造成的N+1查询问题
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DailyUserStatDTO implements Serializable {

    private LocalDate date;

    //当天新增用户数
    private Integer newUserCount;
}
