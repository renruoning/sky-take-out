package com.sky.log;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

/**
 * 把 WARN/ERROR 级别的日志异步写入 sys_log 表。
 * 用独立JDBC连接而不是应用的Druid连接池，避免日志系统的初始化依赖Spring容器的启动时序；
 * 通过 logback-spring.xml 里包一层 AsyncAppender，写库操作在独立线程完成，不阻塞业务请求线程。
 */
public class DbLogAppender extends AppenderBase<ILoggingEvent> {

    private String url;
    private String username;
    private String password;
    private String driverClassName;

    private static final String INSERT_SQL =
            "insert into sys_log (level, logger, thread, message, exception, create_time) values (?, ?, ?, ?, ?, ?)";

    @Override
    public void start() {
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            addError("加载数据库驱动失败：" + driverClassName, e);
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, event.getLevel().toString());
            ps.setString(2, event.getLoggerName());
            ps.setString(3, event.getThreadName());
            ps.setString(4, truncate(event.getFormattedMessage(), 2000));

            IThrowableProxy throwableProxy = event.getThrowableProxy();
            ps.setString(5, throwableProxy == null ? null : ThrowableProxyUtil.asString(throwableProxy));

            ps.setTimestamp(6, new Timestamp(event.getTimeStamp()));
            ps.executeUpdate();
        } catch (Exception e) {
            // 日志系统自身的错误不能再往日志里打（会死循环），只能报给logback自己的状态管理器
            addError("写入sys_log失败", e);
        }
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }
}
