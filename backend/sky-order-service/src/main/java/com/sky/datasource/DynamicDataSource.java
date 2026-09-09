package com.sky.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * determineCurrentLookupKey()返回null时，AbstractRoutingDataSource会退回
 * DataSourceConfiguration里配的defaultTargetDataSource（主库）——这是"没标@Slave的方法
 * 一律走主库"这个行为的实现依据，不需要在每个写方法里手动set(MASTER)
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}
