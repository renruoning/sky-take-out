FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8096
# Seata客户端(GlobalTransactionScanner)启动时会用反射内省Spring/MyBatis生成的JDK动态代理
# （比如shopMapper这类Mapper接口代理），JDK 17默认模块系统不允许这么做，会直接把启动炸掉
# （InaccessibleObjectException: module java.base does not "opens java.lang.reflect"）。
# 只有这个服务接了Seata Saga，其它服务的Dockerfile不用加这个
ENTRYPOINT ["java", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "-jar", "/app/app.jar"]
