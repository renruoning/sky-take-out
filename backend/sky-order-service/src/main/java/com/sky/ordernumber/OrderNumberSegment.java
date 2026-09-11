package com.sky.ordernumber;

/**
 * 一次号段分配拿到的独占区间，[start, end]两端都包含在内
 */
public class OrderNumberSegment {

    private final long start;
    private final long end;

    public OrderNumberSegment(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }
}
