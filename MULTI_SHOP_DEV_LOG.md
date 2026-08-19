# 多商户改造开发记录

记录多商户（多租户）改造过程中遇到的 bug、排查过程，以及几个刻意不做/暂缓的设计决策和原因。按时间顺序整理，方便以后回顾。

## 一、环境 / 工具类问题

### 1. `simplefrontend` 目录首次创建后"消失"
用 Bash 工具的 `mkdir -p` + `Write` 建好 `simplefrontend/` 后，紧接着用 Bash 的 `ls` 却提示目录不存在；用 PowerShell 核实也确认磁盘上没有这个目录。
**原因**：Bash 工具当时的执行环境和真实文件系统之间出现了状态不一致（沙盒/工作目录漂移），导致那一批命令实际没有落盘。
**解决**：改用 `Write` 工具重新写入文件，并且每次都用 PowerShell（不经过之前有问题的 Bash 会话）`Test-Path` 交叉验证确实落盘，才继续下一步。
**教训**：以后凡是"创建了文件但后续步骤怎么看都跟预期不符"，第一时间怀疑是不是写入根本没成功，而不是去改逻辑。

### 2. curl 传中文 JSON 报 "Invalid UTF-8 start byte 0xb6"
测试"新增菜品"接口时，请求体里的中文菜名导致后端 Jackson 反序列化直接抛 `JsonParseException`。
**原因**：不是后端的锅，是本地 Git Bash 终端下 `curl -d '{"name":"中文..."}'` 这种写法，中文字符在经过 shell 转义时被按非 UTF-8 编码重新编码了一遍，字节流本身就是坏的。
**解决**：测试时改用纯 ASCII 菜名（如 `MultiShopTestDish1`）来验证接口逻辑本身没问题，绕开终端编码这个和业务代码无关的干扰项。

### 3. `mvn -pl backend/sky-server -am spring-boot:run` 报 "Unable to find a suitable main class"
想从仓库根目录用 `-pl` 只跑 sky-server 模块，结果 Spring Boot 插件却尝试在最外层的聚合 `pom`（packaging=pom，没有任何 Java 代码）上执行 `run` 目标。
**原因**：`spring-boot:run` 绑定在 `default-cli` 执行上，`-pl` + `-am` 组合在这个项目的多模块结构下没有像预期一样把目标定位到子模块。
**解决**：直接 `cd backend/sky-server` 再执行 `mvn -o spring-boot:run`，不依赖 `-pl`。

### 4. 改了 `sky-pojo`/`sky-common` 后，`sky-server` 却报 `ClassNotFoundException: com.sky.entity.Shop`
新增了 `Shop` 实体类，`sky-pojo` 单独编译能过，但从 `sky-server` 目录直接 `mvn spring-boot:run` 时，MyBatis 解析 `ShopMapper.xml` 报"找不到类型别名 com.sky.entity.Shop"。
**原因**：`sky-server` 的 `pom.xml` 是通过 Maven 坐标（`<artifactId>sky-pojo</artifactId>`）依赖 `sky-pojo` 的，直接在子模块目录下跑 Maven 时，它解析依赖是去本地仓库 `~/.m2` 里找已经 `install` 过的旧 jar，而不是最新的 `target/classes`——新加的 `Shop.class` 根本不在那个旧 jar 里。
**解决**：先在根目录 `mvn install -DskipTests` 把 `sky-common`/`sky-pojo` 最新代码装进本地仓库，再进子模块跑 `spring-boot:run`。以后每次改了 `sky-common`/`sky-pojo` 都要记得先 `install` 一次。

### 5. 平台超管账号 `platform` 登录一直报"密码错误"
手动往数据库插入超管账号时，密码字段直接抄的是"标准 MD5('123456')"，结果一直登录不上。
**原因**：把 MD5 哈希值抄错了一位——真实的 `MD5("123456")` 是 `e10adc3949ba59abbe56e057f20f883e`（32 位），我一开始少抄了最后一个 `e`，变成 31 位的 `e10adc3949ba59abbe56e057f20f883`。
**排查方法**：对比数据库里 `admin` 账号本来就能登录成功的密码哈希，发现两者本该一致但长度不同，才定位到是手抄哈希错了；用 `md5sum`/PowerShell 重新算了一遍确认正确值。
**解决**：更新 `platform` 账号密码为正确哈希，同时把 [database/sky.sql](database/sky.sql) 和 [database/migration_multi_shop.sql](database/migration_multi_shop.sql) 里的种子数据也一并改正。

## 二、设计决策记录（不是 bug，但过程中要考虑清楚）

### 1. `AutoFillAspect` 里 `shop_id` 的自动填充为什么要"有条件"
一开始考虑：只要是 `@AutoFill(INSERT)` 的实体，统一在切面里 `setShopId(BaseContext.getCurrentShopId())`。
**发现的问题**：`Employee` 的插入也走 `@AutoFill(INSERT)`。如果无条件覆盖，超管（`BaseContext.getCurrentShopId()` 为 `null`）新增员工时，指定的目标店铺 `shopId` 会被这个切面用 `null` 覆盖掉，新员工的店铺归属直接丢失。
**最终方案**：切面只在 `BaseContext.getCurrentShopId() != null` 时才反射调用 `setShopId`。这样一来：
- 店铺员工自己新增下属员工时，会被自动填充为自己所在店铺（符合预期，还顺便省了一行 service 代码）；
- 超管新增员工时（`currentShopId` 为 `null`），切面不出手，由 `EmployeeServiceImpl.save()` 显式读取 `EmployeeDTO.shopId` 来设置。

### 2. `dish`/`setmeal` 的 `getById`（按主键查详情）为什么没有直接在 SQL 层加 `shop_id` 过滤
`DishMapper.getById(id)`、`SetmealMapper.getById(id)` 除了被管理端"查询详情"接口用，还被购物车、订单明细构建等内部逻辑按主键直接取值——这些内部调用点不一定有明确的"当前店铺"上下文（比如构建订单时是从购物车的 `shop_id` 取，不是从 `BaseContext`）。如果直接在 Mapper 层强加 `shop_id = #{shopId}`，就要把 `shopId` 参数一路透传进所有内部调用点，改动面明显更大。
**最终方案**：`getById` 保持不限定店铺的裸查询，改为在**管理端相关的 Service 方法**里（`DishServiceImpl.getByIdWithFlavor`、`deleteBatch`，`SetmealServiceImpl.getByIdWithDish`、`deleteBatch`）显式比较 `entity.getShopId().equals(BaseContext.getCurrentShopId())`，不匹配就当成"不存在"处理。效果等价，但改动范围小很多。

### 3. `employee.username` 为什么没有改成按店铺唯一
这是目前**唯一一个明确判断"暂时没法安全修复"而保留原状**的缺口。
员工登录接口（`POST /admin/employee/login`）现在的入参只有 `username` + `password`，没有"选择店铺"这一步。如果把唯一索引从全局 `UNIQUE(username)` 松绑成 `UNIQUE(shop_id, username)`，不同店铺就能各自注册同名用户名（比如两个店都能有一个叫 `manager` 的账号）——但登录时只凭用户名查，`SELECT * FROM employee WHERE username = ?` 会查出两行，MyBatis 单行 `resultType` 直接抛 `TooManyResultsException`；就算改成"随便取一行"，也会出现"用户名相同、密码恰好也相同"时登进别的店铺账号的安全问题。
真正安全的修法需要登录流程本身先确定"店铺"（比如登录页先选店铺，或者按域名/路径区分店铺），这属于前端交互流程的改动，所以按你的要求（本轮不改 frontend）没有动这一块，`idx_username` 继续保持全局唯一。

## 三、本轮新修复的缺口（相对上一版）

- 管理端订单的接单/拒单/取消/派送/完成这几个接口，现在都会先校验订单的 `shop_id` 是否等于当前登录员工的店铺，不匹配统一按"订单不存在"处理，防止跨店操作他人订单。
- 报表（`ReportServiceImpl`）和工作台（`WorkspaceServiceImpl`）涉及的订单/菜品/套餐统计（营业额、订单数、销量榜、菜品/套餐总览）全部按当前员工的 `shop_id` 过滤；超管（`shopId` 为空）调用时不加过滤，可以看到全平台汇总数据（用户新增数统计除外，见下面的"仍然保留的限制"）。
- WebSocket 来单提醒/催单提醒改成按店铺路由：新连接如果在 URL 上带 `?shopId=`，就只会收到自己店铺的消息；不带这个参数的旧连接方式继续收到全部消息（向后兼容，等前端跟进后才能真正做到"店铺之间互不可见"）。

## 四、仍然保留的已知限制

- `employee.username` 全局唯一（原因见上）。
- 用户（`user`）表本身没有 `shop_id`，"新增用户数"这类统计口径继续是全平台维度，不区分店铺——这是"用户账号本来就是跨店铺共享的"这个既定设计决定的自然结果，不算是遗漏。
- `frontend`（Vue 管理后台）和 `simplefrontend`（接口测试台）都还没有跟进新增的 `shopId` 相关参数和平台超管相关接口。
