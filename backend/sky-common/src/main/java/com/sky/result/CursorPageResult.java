package com.sky.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 游标分页（keyset pagination）结果，跟{@link PageResult}是两回事——没有total，因为游标分页故意不做COUNT(*)。
 * 客户端翻下一页时，把这次返回的最后一条记录自身带的排序字段（比如orderTime/id）原样传回来当游标，
 * 不需要额外解析一个专门的cursor字段。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CursorPageResult implements Serializable {

    private List<?> records; // 当前页数据

    private boolean hasMore; // 还有没有更多数据，客户端据此决定要不要展示"加载更多"

}
