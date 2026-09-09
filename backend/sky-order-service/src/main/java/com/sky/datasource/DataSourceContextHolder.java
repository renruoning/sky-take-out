package com.sky.datasource;

/**
 * 读写分离的路由依据，ThreadLocal存当前线程要走哪个数据源。
 * 默认（没设置过/已经clear）返回null——{@link DynamicDataSource#determineCurrentLookupKey()}
 * 拿到null时会退回Spring自己配的defaultTargetDataSource（即主库），所以"不标注@Slave的方法
 * 都走主库"这件事不需要每处显式设置MASTER，只有要走从库的地方才需要显式set(SLAVE)。
 * <p>
 * 这也是为什么"强一致读"（比如下单/支付后立刻要读到准确数据的场景）不需要额外代码：
 * 只要不标{@code @Slave}，什么都不做就是默认最安全的主库读。
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

    private DataSourceContextHolder() {
    }

    public static void set(DataSourceType type) {
        CONTEXT.set(type);
    }

    public static DataSourceType get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
