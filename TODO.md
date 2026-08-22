# TODO：面向生产环境的改造路线图

当前项目是单体应用 + 单个MySQL，能跑通完整业务闭环（多商户/多品类/购物车/下单/评价/发票/AI客服），但离真实工业级设计还有距离。这份清单按"如果这是一个要真正上线、要扛真实流量的项目，会按什么优先级去补"来排的，不是越花哨越好——排序本身就是"先保证不出错，再谈快，最后才谈架构升级"。

## P0：基础可靠性（比性能优化更优先，现在就有实际风险的）

- [x] **下单/支付接口加幂等键**：已实现。`OrderServiceImpl.submit()`用Redis `SETNX`（key按userId，5秒TTL）挡并发重复提交，抢不到锁直接拒绝，而不是让两个请求都读到同一份购物车各建一笔订单；`payment()`在处理前先查订单当前`payStatus`，已支付的订单第二次调用直接拒绝，不会重复走支付成功逻辑、重复推送来单提醒。已用并发curl实测验证：两个同时发的`submit`请求一个成功一个被拒；对同一订单号连续调用两次`payment`，第二次被拒。
- [x] **建立通用限流框架，覆盖全部对外接口**：已实现。新增`@RateLimit(keyType, limit, windowSeconds, name, message)`注解（`sky-server/annotation`）+ `RateLimitAspect`（`sky-server/aspect`）用AOP统一拦截，Redis `INCR`+`EXPIRE`做固定窗口计数，两种维度：`IP`（未登录场景，从`X-Forwarded-For`兜底`getRemoteAddr()`取）、`ACCOUNT`（已登录场景，取`BaseContext.getCurrentId()`，管理端/用户端统一）。原来专门为AI客服写的`AiChatRateLimitInterceptor`已删除，改成在`AiChatController.chat`上标注解，行为等价但换成了可复用的通用机制。目前已挂到几个代表性接口，覆盖TODO里提到的每个类别：
  - 登录（IP，10次/60秒）：`EmployeeController.login`、`UserController.login`——已用连续12次错误密码请求实测：前10次正常返回"密码错误"，第11次起变成"登录尝试过于频繁"
  - AI客服（ACCOUNT，10次/60秒）：`AiChatController.chat`——沿用原来的验证方式，行为不变
  - 写操作（ACCOUNT）：`OrderController.submit`（20次/60秒）、`ReviewController.submit`（5次/60秒）
  - 查询类（IP，120次/60秒，故意放松）：`DishController.list`（用户端菜品查询）作为示例
  - 报表/导出（ACCOUNT，5次/60秒）：`ExportController.export`
  - 还没打注解的其余接口（购物车、地址簿、套餐查询等）不在本轮范围内，框架已经通用可复用，后续要覆盖只是在方法上加一行注解的事，不用再改动核心逻辑
  - 网关层的IP粗粒度兜底仍然要等P4网关落地后再做，当前这层是纯应用层拦截
- [x] **下游依赖故障熔断**：已完成审计并修复了实测到的两个真实问题（不是泛泛过一遍，是照着"Redis/RabbitMQ挂了会怎样"真的把Redis关掉测的）：
  - **限流/幂等对Redis访问全部改成fail-open**：`RateLimitAspect`和`OrderServiceImpl.submit()`的幂等锁访问Redis失败时，记日志、放弃这次限流/幂等校验、直接放行，而不是让异常往上抛把整个请求搞挂。
  - **`dishCache`等`@Cacheable`/`@CacheEvict`访问Redis失败时不再抛异常**：新增`LoggingCacheErrorHandler`（`sky-server/handler`），`RedisConfiguration`实现`CachingConfigurer`接管`errorHandler()`，get失败按缓存未命中处理（回源查DB），put/evict失败只是记日志、不影响正确性。
  - **下单时发送RabbitMQ延迟消息（订单超时取消）改成try/catch，broker不可达只记日志，不影响订单创建成功**：之前这段代码没有任何异常处理，RabbitMQ一旦不可达会导致整个`submit()`事务回滚，等于"消息队列挂了直接不能点餐"。
  - **实测验证**：用`docker stop redis`把Redis整个关掉后，依次调用下单提交、AI客服对话、菜品查询（分别命中幂等锁/限流/限流+缓存三种Redis依赖路径），三个接口全部正常返回成功结果，backend日志里能看到对应的"访问Redis失败，本次放行（fail-open）"记录。跟这条TODO写下时"Redis挂了`submit()`直接抛异常变成未知错误"的实测结果对比，是实打实的行为改变，不是纸面上的设计。
  - RabbitMQ本身仍是单实例、订单状态事件广播也还没做（P2里的内容），这条item解决的是"依赖不可用时别把主流程拖挂"，不等于消息队列本身已经高可用。
- [x] **可观测性**：做了两件低成本但实际有用的事，没有去接ELK/Prometheus/SkyWalking这类需要额外部署基础设施的重型方案（demo阶段没必要，真要接入也不影响下面这两点白做）：
  - **全局访问日志**：新增`RequestLoggingInterceptor`，注册在jwt拦截器之后（这样它的`afterCompletion`能在`BaseContext`被jwt拦截器清空之前先执行，才能拿到操作人id），每个请求打一行`方法/路径/状态码/耗时/操作人`的汇总日志，不用再从一堆mapper debug日志里自己拼凑"这次请求到底发生了什么"。
  - **请求级traceId**：同一拦截器给每个请求生成一个traceId写进MDC，`logback-spring.xml`的控制台输出格式加了`[traceId]`字段，同一次请求在controller/service/mapper各层散落的日志行现在能用这个字段串起来看。这个字段本身就是为以后接ELK/Loki留的钩子，接入时不需要再回头改代码。
  - 已实测：`docker stop redis`测试期间backend日志里能看到`POST /user/order/submit status=200 duration=...ms operator=73`这样的行，确认功能生效。
  - 指标监控（Prometheus+Grafana）和链路追踪（SkyWalking/Zipkin）仍然没做，这两个需要额外部署监控基础设施，留在真正要上线时再做。
- [x] **连接池/线程池按容量规划配置**：`application.yml`里给Tomcat（`server.tomcat.threads.max=50`、`min-spare=10`、`accept-count=100`）和Druid（`initial-size=5`、`min-idle=5`、`max-active=20`、`max-wait=3000`）都加了显式配置和注释说明理由，不再依赖框架默认值——顺带发现Druid不显式配置时`max-active`默认只有8，对这个项目来说明显偏小（前面测并发下单时稍微多开几个连接就可能顶到）。这里的数字是按demo/作品集评审规模估的，不是走了完整的P5容量规划流程；真要撑真实流量，这些数字需要按本文档最后的估算方法+实际压测重新核定。

## P1：缓存层

- [x] **购物车迁移到Redis**：已实现，彻底不落MySQL了。`ShoppingCartServiceImpl`改成用`StringRedisTemplate`操作Redis Hash（`shopping_cart:{userId}` → field按`dishId/setmealId/dishFlavor`算出的稳定标识 → 整行JSON），每次写操作后刷新7天TTL。原来的`ShoppingCartMapper`/`ShoppingCartMapper.xml`已删除；`OrderServiceImpl`里下单读购物车、清空购物车、"再来一单"批量加购物车，全部改成调`ShoppingCartService`而不是直接碰mapper。已实测：加购物车两次数量变2、减一次变1、下单后购物车清空、Redis里直接`HGETALL`能看到JSON格式的行。这个功能现在完全依赖Redis可用——没有MySQL兜底了，这是迁移的必然代价，不是遗漏。
- [x] **地址簿走cache-aside缓存**：已实现，但缓存的是`getById(Long id)`而不是`list()`——真正的高并发入口是下单结算时按`addressBookId`查一次收货信息（`OrderServiceImpl.submit()`），这条已经从直接调`AddressBookMapper`改成调`AddressBookService.getById()`，冲的是这个热路径，不是"查看我的地址列表"那个低频页面。`update`/`deleteById`精确清对应id的缓存；`setDefault`会把用户名下所有地址的`is_default`一起改一遍，没法精确定位，用`allEntries=true`整体清空（低频写，代价可接受）。已实测：调用后Redis里能看到`addressBookCache::4`这个key。
- [x] **店铺基础信息缓存**：已实现。`ShopServiceImpl.listActive(businessType)`加了`@Cacheable`，`save`/`update`/`startOrStop`清缓存。`businessType`可能是null（查全部店铺），用了SpEL三元表达式兜底成`'ALL'`——第一次实测直接踩到了Spring Cache"key不能是null"的坑，已经改好并重测通过。
- [x] **审视现有`dishCache`的失效策略**：做了两件事——穿透防护：`DishServiceImpl.listWithFlavor`先用`CategoryMapper.existsById`判断分类id是否真实存在，编造的id直接返回空、不查dish表，且这个空结果本身也会被`@Cacheable`缓存住（同一个编造id第二次开始连这次判断都不用走）；雪崩防护：新增`JitteredRedisCacheWriter`包一层默认的`RedisCacheWriter`，给所有走Spring Cache的key的TTL加±10%随机抖动，不再是所有key整点同时过期。这两个改动是全局的，不止对`dishCache`生效，`addressBookCache`/`shopListCache`/`shopRatingSummaryCache`都一起受益。已实测：查一个不存在的categoryId返回空数组且被缓存；查`addressBookCache::4`的TTL是3783秒而不是固定的3600秒，抖动确实生效了。（P0已经顺带解决了"Redis本身不可达时会不会拖垮请求"这一半——见`LoggingCacheErrorHandler`；这次做的是另一半，跟Redis可用性无关，是缓存策略本身的问题。）
- [x] **评价列表/店铺平均分**：已实现，但不是走MQ异步更新汇总表，而是更简单的cache-aside：`ReviewServiceImpl.getShopRatingSummary(shopId)`加`@Cacheable`，提交评价时用`CacheManager`手动清掉对应shopId的缓存（shopId是方法内查出来的局部变量不是入参，没法用`@CacheEvict`的SpEL直接引用）。之前这是`/user/shop/list`里对每个店铺都要跑一次的实时`avg()`+`count()`聚合，一个真实的N+1性能问题，现在变成读多写少的缓存命中。已实测完整链路：提交评价前`shopRatingSummaryCache::1`存在→提交评价→缓存被清掉→再查店铺列表，`reviewCount`从1变2、缓存重新回填。
- [ ] **分布式锁**：用Redisson实现，防止多实例/多服务同时处理同一订单（比如订单超时取消和用户手动取消同时触发）。**这次没做**：现在就是单实例部署，这个场景下不存在"两个实例抢同一个订单"的问题，P0已经用简单的`SETNX`解决了"同一用户并发重复提交"这个单实例下真实存在的问题。Redisson分布式锁的价值要等到真的水平扩了不止一个实例才体现，现在做了也没法真正验证是否有效（没有第二个实例可以拿来测竞争），先记在这里，等P4真要扩实例或拆分服务时再做，到时候`submit()`那个`SETNX`锁也一并升级成Redisson的`RLock`。
- [x] **限流计数器落地到Redis**：P0的通用限流框架已经是这么实现的（`RateLimitAspect`用`INCR`+`EXPIRE`固定窗口），不是单机内存计数器。严格说固定窗口不如滑动窗口精确（窗口边界处理论上能连续两倍量），也没用Redisson的`RRateLimiter`，如果以后要更精确的限流算法（滑动窗口/令牌桶）可以在这个基础上换实现，接口（`@RateLimit`注解）不用变。
- [x] **幂等键存储**：P0的下单幂等已经是这么实现的（`OrderServiceImpl.submit()`用`SETNX`+TTL）。目前只覆盖了"防止同一用户并发重复提交"这一种幂等场景，还不是通用的"任意写请求按客户端生成的idempotency-key去重"机制——如果以后有更多接口需要幂等保护，可以抽出一个类似`@RateLimit`的通用注解+切面，现在是按需在`submit()`里单独写的。
- [ ] **Redis高可用**：现在是单实例，一旦挂了缓存、分布式锁、限流会一起瘫。**这次没做，也不打算现在做**：这是纯粹的基础设施部署决策（要不要跑Sentinel/Cluster），不是能靠改代码解决的问题，而且现在购物车已经完全依赖Redis了（上面那条），Redis单点故障的影响面比这份TODO刚写下时还要大一些——真要上线或者需要更高可用性的时候，这条要优先于其他P1里"锦上添花"性质的项。

## WebSocket 实时推送（现在的实现有真实安全漏洞，不是锦上添花）

- [x] **握手阶段做鉴权，不能信任客户端自报的shopId**：已实现。`WebSocketServer.onOpen`现在要求连接URL带`token`查询参数，用admin端JWT密钥解析，`shopId`只认token里的claim（不存在则视为平台超管，可见所有店铺），token缺失或校验失败直接`session.close()`（返回码1008）。已用Node WebSocket客户端实测三种场景：不带token→拒绝；伪造token→拒绝；`boss1`的真实token→正常建立连接。
  - [x] **顺带发现的遗留缺口**：`admin.html`前端之前完全没有连接`/ws/`的代码，已补上——登录成功（含刷新页面后靠本地存的token自动进入）后调用`connectWebSocket()`，用`ws://.../ws/{sid}?token=`带上鉴权token建立连接，收到消息按`type`区分来单提醒/催单用已有的`toast()`提示，当前正停在订单页时顺带自动刷新列表；断线（非主动登出）3秒后自动重连，登出时主动断开不重连。已实测验证：boss1连接后，真实走一遍下单+支付流程，`type:1`来单提醒消息实时推到了这个连接上；同时验证了店铺隔离仍然生效——boss2（shop2）连接期间给shop1的订单支付，boss2完全收不到消息。
- [ ] **连接状态从本地内存迁移到Redis**：`SESSION_MAP`/`SID_SHOP_MAP`现在是静态`ConcurrentHashMap`，状态锁在单个JVM实例里。一旦水平扩了不止一个实例，员工连的是实例A的WebSocket，但触发通知的下单请求被负载均衡分到实例B处理，实例B内存里没有这个连接，通知直接丢失。解决办法是复用P1/P2里已经规划的Redis和MQ基础设施——业务事件发布到MQ（或用Redis Pub/Sub），每个实例都订阅，各自只推送给自己本地持有的连接，不需要另外造一套机制。
- [x] **真正的心跳存活检测**：已实现。新增`LAST_ACTIVE_MAP`（sid→最近一次建连/心跳时间戳），`onOpen`/`onMessage`都会刷新；新增`@Scheduled(fixedRate=30秒)`的`cleanupStaleSessions()`定时任务，扫描超过75秒（前端每25秒发一次心跳，容忍错过2次）未活跃的连接，主动`session.close()`并清理`SESSION_MAP`/`SID_SHOP_MAP`/`LAST_ACTIVE_MAP`三个map，不用等下次`onClose`才清理。顺带给`sendToAllClient`/`sendToShopClients`的推送失败分支也加了同样的清理逻辑——推送时如果连接已经死了（`sendText`抛异常），不用等心跳超时窗口，直接清掉，避免死连接在这之前的窗口期里继续被无意义地重试推送。已实测：连一个WebSocket客户端后故意不发心跳，97.5秒时收到服务端主动断开（`code=1001, reason="heartbeat timeout"`），落在理论区间[75s, 75s+30s扫描间隔]内；后端日志确认打印了"心跳超时（超过75000ms未活跃），主动断开清理"。

## P2：异步化 / 消息队列

- [x] **订单状态变更事件广播**：已实现，复用了已有的RabbitMQ和`ORDER_EXCHANGE`。之前`paySuccess()`（来单提醒）和`reminder()`（客户催单）都是在改完订单状态的同一个方法里直接同步调`webSocketServer.sendToShopClients()`，现在改成"改状态"照旧同步走本地事务，"发通知"变成往新加的`sky.order.event.notify.queue`发一条事件消息，由新的`OrderEventNotifyListener`异步消费后再推WebSocket——支付/催单接口的响应不再跟WebSocket推送的成败/快慢绑在一起。已实测完整链路：连一个WebSocket客户端，走支付、走催单，两次都在异步消费之后正确收到了对应的`type:1`/`type:2`推送。
- [x] **死信队列（DLQ）**：已实现，但只加在这次新建的通知队列上，没有回头改老的`orderTimeoutQueue`（详见下面的范围说明）。`sky.order.event.notify.queue`配置了`x-dead-letter-exchange`指向新建的`sky.order.event.dlx`，绑定死信队列`sky.order.event.notify.dlq`；消费失败会重试3次（1s起步、指数退避到10s封顶，`orderEventListenerContainerFactory`），重试耗尽后reject-and-dont-requeue，消息就会被转发到DLQ，而不是无限重试形成死循环或被broker默默丢弃。已用`rabbitmqctl list_queues name arguments`确认四个队列（含新的DLQ）都正确声明，`sky.order.event.notify.queue`的`x-dead-letter-*`参数指向了DLX。
- [x] **消费端幂等**：已实现。`OrderEventNotifyListener`给每条消息生成一个`messageId`（发布方`OrderServiceImpl.publishOrderEvent()`用`UUID.randomUUID()`），消费时先用Redis `SETNX`（10分钟TTL）判断这个messageId是否处理过，处理过直接跳过。已用RabbitMQ管理API直接往交易所重发一条`messageId`和已处理过的某条消息相同的伪造消息，模拟"同一条消息被重复投递"——连着的WebSocket客户端这次没有收到第二次推送，日志里能看到"消息xxx已经处理过，跳过"。Redis不可用时这个判断本身fail-open（当作没处理过，直接放行推送），不能因为幂等校验访问不到Redis就把通知漏发。
- [x] **发票生成、AI对话历史落库是否要异步化**：评估结论是不值得，没有改。发票申请（`InvoiceServiceImpl.apply`）就是几个字段校验加一条insert，本身极快，用户提交后期望立刻拿到发票详情用于跳转打印页，改成异步还得另外加"生成中/生成完成"的状态轮询或推送机制，纯粹增加复杂度换不来任何实际的响应时间改善。AI对话历史的落库同理：真正慢的是调用OpenAI那一步（实测经常1-3秒），插入`ai_message`这一行DB操作跟它比快得可以忽略不计，而且这行数据必须在返回AI回复之前就已经关联好`conversationId`，没法真的摘出去异步做。
- [ ] **RabbitMQ高可用**：现在单实例，一旦挂了，依赖它的下单超时取消、这次新加的订单事件通知全部失效——不过前面P0已经确认过这条链路本身是fail-open的（RabbitMQ挂了下单和支付/催单接口本身不会跟着挂，只是超时自动取消和来单提醒推不出去）。**这次没做**：跟Redis高可用一样，这是纯粹的基础设施部署决策（要不要跑镜像队列/仲裁队列集群），不是改代码能解决的，真要上线或者需要更高可用性的时候再动。

## P3：数据库层面

- [ ] **报表查询迁移出业务库**：现在`ReportServiceImpl`直接对`orders`表做聚合统计，业务量小的时候没事，量一大会拖慢下单主流程共用的同一个数据库。真实做法是把数据同步到独立的数据仓库/OLAP（ClickHouse等），报表查询完全跟交易库隔离。
- [ ] **读写分离**：报表、评价列表、菜单浏览这类只读场景走从库，下单/支付这类写操作留在主库。
- [ ] **订单表分库分表规划**：现在单表，先不用真的拆，但要想清楚拆分维度（按`shop_id`还是按时间），免得数据量上来了要重构整个订单模块。

## P4：微服务拆分（确定要做）

不再是"量级倒逼才做"的可选项，是既定目标。顺序上仍然放在P0-P3之后，是因为拆分的前提是先有清晰的服务边界和可靠性基线——现在单体里到处手写的shopId校验、缓存策略、事件流转，都得先在P0-P3理清楚，拆分的时候才有章可循，不然只是把同样的混乱换个部署形态。

- [x] **第一步：把AI客服拆成独立微服务**：已实现，是"服务边界划分""每个服务独立数据库""服务注册发现+API网关"这三条的第一次真实落地，不是纸面设计。
  - **新增两个模块**：`backend/sky-ai-service`（独立的Spring Boot应用，`AiChatController`/`AiChatService`/两个Mapper原样从sky-server搬过去，package路径不变所以import不用改；额外复制了一份`RateLimitAspect`/`GlobalExceptionHandler`/`JwtTokenUserInterceptor`——没有把它们抽到sky-common共享，因为sky-common目前没有spring-web/spring-data-redis依赖，为这几个小类扩大共享库的依赖面性价比不高，两个服务各自维护一份在这个规模下是合理的取舍）；`backend/sky-gateway`（纯路由，WebFlux技术栈，不依赖sky-common/sky-pojo）。`RateLimit`注解本身挪到了`sky-common/annotation`（这个是零风险的移动，纯注解定义不需要新依赖），两个服务共用同一个注解类型。
  - **注册中心+网关**：Nacos（`nacos/nacos-server:v2.2.3`容器，standalone模式；Windows Docker Desktop下这个镜像默认会因为cgroup v2探测触发JVM的一个已知NPE导致启动失败，加`JAVA_TOOL_OPTIONS=-XX:-UseContainerSupport`环境变量绕过）+ Spring Cloud Gateway（`lb://服务名`按Nacos服务发现路由，`/user/ai/**`转发到`sky-ai-service`，其余`/**`转发到`sky-server`，路由按声明顺序匹配，更具体的规则必须排在前面）。Spring Boot 2.7.3对应Spring Cloud `2021.0.8` + Spring Cloud Alibaba `2021.0.5.0`。
  - **端口重排，前端零改动**：`sky-server`从8090挪到8091，`sky-ai-service`用8092，`sky-gateway`接管8090。`frontend/conf/nginx.conf`里的`upstream webservers`本来就指向127.0.0.1:8090，现在这个端口是网关而不是sky-server，**nginx.conf和所有前端JS代码一个字都没改**，重启nginx后透明生效。
  - **数据库物理拆分**：新建独立的`sky_ai_service`库（见`database/sky_ai_service.sql`），`ai_conversation`/`ai_message`两张表连同已有的真实数据一起用同实例跨库insert-select原样搬过去（`database/migration_split_ai_service_db.sql`，AUTO_INCREMENT也对齐了，不是从空库重新开始），确认无误后从`sky_take_out`主库里删掉（`database/migration_drop_ai_tables_from_main_db.sql`，`sky.sql`同步更新加了说明注释）。sky-ai-service用Spring Boot默认的Hikari连接池，没有像sky-server那样接Druid——demo规模没必要为一个小服务重复一遍连接池调优。
  - **过程中踩的坑**（都是真遇到的，不是提前预料到照抄的）：①`RateLimitAspect`用sky-server那套`RedisTemplate<String,Object>`在新服务里找不到对应bean（sky-server是自己在`RedisConfiguration`里显式定义的这个类型，sky-ai-service没有），改成Spring Boot自动配置好的`StringRedisTemplate`，限流场景本来也只需要存字符串计数。②`sky-pojo`模块传递依赖了knife4j（给实体加Swagger注解用），这个服务没打算接swagger UI，但knife4j的springfox自动配置还是会跟着sky-pojo这条依赖链混进来，触发一个`PatternsRequestCondition`相关的启动期NPE（springfox和Spring Boot 2.6+新版路径匹配器的已知不兼容），在sky-ai-service的pom里排除掉这条传递依赖解决。③Gateway最初对所有请求返回503，`nacos-discovery`起步依赖不会自动带上响应式负载均衡器，补充`spring-cloud-starter-loadbalancer`依赖后`lb://`路由才能正常解析服务实例。
  - **实测验证**：三个服务全部注册进Nacos且状态健康（`curl localhost:8848/nacos/v1/ns/service/list`能看到`sky-server`/`sky-ai-service`/`sky-gateway`三个）。全部通过网关（不直连各服务端口）测试：`POST /admin/employee/login`、`GET /user/shop/list`正确路由到sky-server；完整走一遍AI客服流程（新建会话拿到真实OpenAI回复、`conversationId`延续上下文、另一用户访问被拒"会话不存在"、连续发12条触发限流）正确路由到sky-ai-service，且新会话数据确认落在`sky_ai_service`库而不是主库；WebSocket（`ws://localhost:8090/ws/{sid}?token=`）穿过Gateway正常握手，并且完整验证了催单推送这条异步链路（Gateway→sky-server→RabbitMQ→`OrderEventNotifyListener`→WebSocket推送）端到端可用；重启本地nginx后，`http://localhost/`整个链路（nginx→gateway→两个后端服务）在零前端代码改动的情况下功能不变。
  - **明确没做的部分**：发票/评价/订单/商品服务的边界拆分（下一步的下一步）、分布式事务方案、服务间RPC通信（AI客服这次不需要调用其它服务，也没有其它服务需要调用它）、Nacos自身的高可用（跟Redis/RabbitMQ高可用一样，单实例部署，真要上线时再处理）。
- [ ] **下一步：拆发票/评价服务**（依赖订单但自身逻辑独立，比AI客服多一层"读订单数据"的耦合，比订单/商品简单）→ 最后拆订单/商品这两个耦合最深的核心模块。
- [ ] **服务间通信**：强一致场景（比如下单时校验商品价格）用RPC（Dubbo/gRPC），弱一致场景走消息队列做最终一致性，复用P2里已经在用的RabbitMQ。这次AI客服拆分完全不需要这个——它不调用别的服务，别的服务也不调用它，是留到下一个服务拆分时才会真正碰到的问题。
- [ ] **分布式事务**：跨服务下单（扣库存+建订单+清购物车）不再是一个本地事务能搞定的，需要引入Seata或者基于消息队列的最终一致性方案（本地消息表/事务消息），提前想清楚用哪种。

## P5：容量规划与压测

- [x] **基础压测，验证P0连接池配置的估算值**：没装JMeter/Gatling（这台机器上没有，临时装一个重型工具换来的信息量性价比不高），改用一个自己写的Node并发压测脚本（固定并发数循环打指定URL，统计QPS和延迟分位数）直接打`GET /user/shop/list`这个真实的、走`shopListCache`缓存的接口。结果印证了P0时对Tomcat`threads.max=50`的估算：并发10→QPS 676（p50 11ms）；并发50→QPS 1177（p50 35ms）；并发150→QPS 1180（几乎不再涨，p50涨到105ms）；并发300→QPS 1201（p50涨到205ms）。并发超过50之后吞吐量基本封顶、延迟跟并发数近似线性增长，是线程池打满、请求在排队等线程的典型特征，跟`server.tomcat.threads.max=50`这个配置直接对得上——数字本身不是重点，"配置的估算值和真实压测结果吻合"这件事才是这条TODO真正要验证的。
- [x] **压测过程中顺带挖出一个真实的AOP排序bug（限流对缓存命中的请求完全失效）**：给`DishController.list`同时打了`@Cacheable(dishCache)`和`@RateLimit(120次/60秒)`。压测时发现对同一个`categoryId`连续请求130次，Redis里的限流计数器只增长到1——因为Spring默认按声明顺序给两个切面排序，`@Cacheable`的缓存拦截器排在`RateLimitAspect`外层，缓存命中直接短路返回，请求根本不会走到限流逻辑。等于说"查同一个分类第二次开始，这个接口的限流形同虚设"，而这恰好是最常见的真实访问模式（用户反复刷同一个分类）。修复：`RateLimitAspect`加`@Order(Ordered.HIGHEST_PRECEDENCE)`让它排在最外层，保证不管缓存命中与否都先计数。加`@Order`之后触发了另一个问题——`@Around("@annotation(rateLimit)")`这种把注解实例绑定成方法参数的写法在多切面显式排序时会抛`Required to bind 2 arguments, but only bound 1`（Spring AOP在AspectJ参数绑定和多层代理链交互时的已知问题），改成纯类型pointcut`@Around("@annotation(com.sky.annotation.RateLimit)")`+反射从`MethodSignature`拿注解，绕开了参数绑定。修复后实测：连续130次同分类查询，前120次成功、第121次开始精确触发"请求过于频繁"；换一个新分类validates缓存本身没被这次改动破坏（两次查询都正常返回）；登录接口的限流（不涉及缓存）行为不变，10次错误密码后第11次限流依旧生效。这个bug会影响后续任何"限流+缓存叠加在同一个方法上"的接口，不止`dishCache`这一个。
- [ ] 针对项目里明确存在的"促销式流量脉冲"场景（比如参考"疯狂星期四"这种规律性大促），做专项的缓存预热和限流预案演练——这个需要先有一个像样的压测工具链（上面那条只是验证了单接口的粗略容量，不是完整的场景化压测），留到真要做大促预案时再投入

## 已实现但此前未做过完整回归的产品功能（本轮补测）

- [x] **AI客服（接OpenAI）**：代码此前已经写好（`AiChatController`/`AiChatServiceImpl`/`OpenAiUtil`等，`ai_conversation`/`ai_message`两张表），但一直是未提交的工作区改动，没有做过一次完整链路的回归测试。这次补测：`POST /user/ai/chat`不传`conversationId`发消息，拿到真实OpenAI回复且正确建会话；传已有`conversationId`追问"我上一句问的是什么"，AI正确复述了上一句内容，证明历史上下文确实被拼进了请求；`GET /user/ai/messages`能看到完整两轮对话入库；另一个用户（user6）尝试读/写user5的`conversationId`，均返回"会话不存在"，归属校验生效。`customer.html`里的悬浮客服面板前端代码也过了一遍，`apiPost`的错误处理约定（`code!==1`即抛错）和后端返回结构完全对得上。
- [x] **发票申请**：同样是此前写好但未提交/未测的代码（`InvoiceController`/`InvoiceServiceImpl`，`invoice`表）。补测：给一笔已完成订单申请发票成功，金额/订单号/店铺名快照正确；`GET /user/invoice/list`能看到；另一个用户尝试为不属于自己的订单申请发票，被拒绝（"只能为自己的订单申请发票"）；同一订单重复申请第二次，被拒绝（"该订单已申请过发票"），去重生效。

## 安全与合规（真要上线才需要，demo阶段可以往后放）

- [x] **敏感字段加密存储**：已实现。新增`FieldCryptoUtil`（AES-256-GCM，key对配置的口令做SHA-256得到，密文里带随机IV所以同一个明文每次加密结果都不一样），加密范围是`employee.phone`/`employee.id_number`/`address_book.phone`——这三个是真正有实际读写路径的PII字段。`user.phone`/`user.id_number`两个字段经确认从来没有被任何代码读写过（原始微信登录设计留下的死列），没有处理，加密一个没人用的字段没有意义。密钥通过`FIELD_ENCRYPTION_KEY`环境变量配置（`SecurityProperties`），有一个demo默认值方便本地跑，但真实部署必须覆盖成自己的密钥，不然等于没加密。列宽从`varchar(11)`/`varchar(18)`改成`varchar(255)`（密文比明文长很多），见`database/migration_encrypt_sensitive_fields.sql`。旧数据没有做批量重加密，解密时如果发现这行本来就是明文（不是合法密文），会原样返回而不是报错，新写入的数据才会真正加密——这是为了不让存量数据直接导致接口500。已实测：新建员工后直接查DB，`phone`/`id_number`是一串base64密文，走API读出来是正确的明文；新增地址同理；老地址（改动前就存在的明文数据）读取正常没有报错；下单时依然能拿到正确的收货人手机号写进订单表。**范围说明**：`orders.phone`（订单快照的收货电话）没有加密，这是有意的权衡——订单是历史凭证，加密后台账/客服查单会变复杂，风险收益比不划算；商户联系客户本来就需要看到这个号码。
- [x] **越权访问的专项安全测试**：审计过程中真的挖出了两个可复现的跨店铺越权漏洞，不是走查代码走个过场：
  - `DishServiceImpl.updateWithFlavor()`：更新菜品这一步本身有`shop_id`限定WHERE、跨店传别人的菜品id会静默不生效，但紧接着"删除口味表原有记录、重新插入新口味"这两步是单独按`dishId`执行的，完全没做归属校验——任何店铺的员工传一个别的店铺的`dishId`，就能清空并替换掉那道菜的口味选项。
  - `SetmealServiceImpl.update()`：完全一样的漏洞模式，套餐本身的更新被正确限定了店铺，但"删除套餐菜品关联、重新插入"这两步不校验归属，能让任意店铺员工替换掉别人套餐里包含的菜品。
  - 两处都补了同样的修复：级联操作之前先按id查一次，校验`shopId`匹配当前登录员工，不匹配直接抛"不存在"异常（复用已有的`DeletionNotAllowedException`，跟同文件里`deleteBatch`的错误处理风格一致）。
  - 顺带审计了其余所有用到`BaseContext.getCurrentShopId()`的地方（订单confirm/reject/cancel/delivery/complete、评价回复、分类/员工的增删改、报表/工作台统计）——这些全部已经在mapper的SQL WHERE子句里正确带了`shop_id`过滤，或者在service层显式做了归属校验后再操作，没有发现同类问题。
  - 已实测两个真实的攻击场景：boss2（shop2员工）尝试用boss1（shop1）的菜品id/套餐id发起跨店更新请求，均被正确拒绝（返回"不存在"），且事后确认shop1的数据完全没被改动。
- [x] **真实微信支付接入**：仍然是mock状态，这次没有改。需要走完整的商户资质+PCI合规流程，属于业务/商务层面的前置条件（要真的注册微信支付商户号），不是能单靠改代码解决的问题，跟这次做的另外两条性质不一样，先留在这里。
