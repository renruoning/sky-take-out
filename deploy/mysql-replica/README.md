# sky_order_service 读写分离：MySQL主从复制

只给`sky_order_service`这一个库做了真实的binlog复制，作为读写分离的演示（跟发票服务水平扩容那次一样，只选一个代表性场景，没有铺开到全部6个库）。主库还是这台机器上原生装的MySQL（不在Docker里，`server_id=1`，`log_bin`本来就是`ON`的、`binlog_format=ROW`，不需要改配置也不需要重启主库），从库是一个新起的`mysql:8.0`容器，走原生异步binlog复制（没用GTID，因为主库`gtid_mode=OFF`，改这个要重启主库，不划算，用经典的`binlog文件名+位点`方式一样能做全部这次要验证的东西）。

## 1. 主库建复制账号（只需要做一次）

```sql
CREATE USER 'repl'@'%' IDENTIFIED WITH mysql_native_password BY 'replpass123';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
```

## 2. 起从库容器

```bash
docker run -d --name sky-mysql-replica \
  -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  mysql:8.0 \
  --server-id=2 \
  --log-bin=mysql-bin \
  --relay-log=relay-bin \
  --read-only=1 \
  --replicate-do-db=sky_order_service
```

`--replicate-do-db=sky_order_service`限定只复制这一个库，不用把主库另外5个库也搬过来。`--read-only=1`防止有人手滑直接写从库（`root`账号仍然能绕过`read_only`，只是普通账号会被挡，demo环境够用）。

## 3. 用一致性快照给从库灌初始数据

```bash
mysqldump -uroot -proot -h127.0.0.1 -P3306 --single-transaction --source-data=2 \
  --databases sky_order_service > sky_order_service_snapshot.sql

mysql -uroot -proot -h127.0.0.1 -P3307 < sky_order_service_snapshot.sql
```

`--single-transaction`拿一致性快照不用锁表（`FLUSH TABLES WITH READ LOCK`），`--source-data=2`会在dump文件里自动写一行注释`-- CHANGE MASTER TO MASTER_LOG_FILE='...', MASTER_LOG_POS=...;`，记录的是这次快照对应的精确binlog位点——不用自己另外掐时间点`SHOW MASTER STATUS`再猜会不会跟dump有竞态。

## 4. 从库指向主库、开始复制

```sql
-- 从dump文件里刚才那行注释抄MASTER_LOG_FILE/MASTER_LOG_POS
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='host.docker.internal',
  SOURCE_PORT=3306,
  SOURCE_USER='repl',
  SOURCE_PASSWORD='replpass123',
  SOURCE_LOG_FILE='LOTUSPC-bin.000085',
  SOURCE_LOG_POS=1123,
  GET_SOURCE_PUBLIC_KEY=1;
START REPLICA;
```

`SOURCE_HOST`用`host.docker.internal`——这是从容器里的MySQL回连宿主机上的主库，跟这个项目其它容器（Seata Server等）统一用的寻址方式一致。`GET_SOURCE_PUBLIC_KEY=1`是因为主库默认`caching_sha2_password`认证插件在明文连接下需要这个，不加会连不上（实际用的`repl`账号显式指定了`mysql_native_password`，理论上不需要，加上更保险）。

## 5. 验证复制在正常工作

```sql
-- 在从库上
SHOW REPLICA STATUS\G
-- 关注：Replica_IO_Running: Yes，Replica_SQL_Running: Yes，Seconds_Behind_Source: 0
```

实测：在主库插入/删除一行，几秒内从库能查到同样的变化（insert和delete都测过）；又用"临时STOP REPLICA冻住从库、只在主库写一行新数据、比对主库/从库/应用@Slave接口三方结果"这个手法做了更硬核的验证，见REPORT.md"读写分离"那条的完整记录——这个验证方式能确认应用真的在物理上连到了从库，而不是代码逻辑上以为在连从库、实际上Hikari连接池悄悄退回了主库这种没测出来就不知道的bug。

## 补充说明

- 从库用的账号密码demo环境直接复用了主库的`root`/`root`——真生产会给从库单独建一个只读账号，权限收窄成`SELECT`。
- 这套复制没有做成永久基础设施，是这次读写分离验证专门搭的；如果之后不需要了，`docker rm -f sky-mysql-replica`即可，不影响主库（复制账号`repl`留着不会有副作用，不用特地删）。
