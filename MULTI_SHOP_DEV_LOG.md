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

## 五、多品类 + 用户评价（第三阶段）开发记录

### bug：营业类型"一年内最多改一次"的冷却期从建店那一刻就开始算了
第一版实现里，`ShopServiceImpl.save()` 建店时就把 `business_type_updated_at` 设成了当前时间。结果是：店铺刚建好、一次营业类型都还没真正改过，紧接着第一次修改就被"距离上次修改不足一年"拦下了。
**根因**：把"建店时选的初始营业类型"也当成了一次"修改"。
**修复**：建店时 `business_type_updated_at` 留空（`null`），只有在 `update()` 里检测到营业类型真的发生变化时才去设置这个时间戳——冷却期应该从第一次真实修改算起，不是从建店时间算起。改完之后验证：建店后第一次改类型成功、紧接着第二次改被拒绝、只改店名不改类型完全不受影响。

### 排查方法记录：怎么在没有真实微信授权的情况下测试用户端接口
用户端登录走真实微信 `code`→`openid` 流程，沙盒环境发不出真实微信请求。为了验证 `/user/**` 这些需要登录态的接口，手动用 PowerShell 的 `HMACSHA256` 现算了一个和后端签名算法、密钥（`application.yml` 里的 `user-secret-key: itheima`）完全一致的 JWT（claims 里塞一个数据库里已存在的 `user.id`），绕开真实微信登录去拿一个合法 token 测试。这不是给生产用的，纯粹是开发联调手段，记录下来是因为这个思路以后调试别的"第三方授权登录"功能时还能用。

## 六、AI 客服聊天

一开始按 Claude API 设计了一版方案，后来发现你手上是 OpenAI 的 key、没有 Anthropic key，改成接 OpenAI Chat Completions REST 接口（`gpt-4o-mini`）。技术选型上没引入 OpenAI 官方 SDK 或任何新的 Maven 依赖，照抄项目里 `WeChatPayUtil`/`HttpClientUtil` 现成的"Apache HttpClient + fastjson"风格新写了一个 `OpenAiUtil`，改动面最小。

`OPENAI_API_KEY` 全程没有出现在任何代码或配置文件里，`application-dev.yml` 里写的是 `${OPENAI_API_KEY:}` 占位符，真正的 key 只存在于你自己设置的系统环境变量里。

实测效果：多轮对话上下文保持正常（第二轮问"你刚才说的第一个类目是什么"，AI 能正确答出第一轮回复里提到的第一项）；跨用户访问别人会话被正确拒绝（返回"会话不存在"，不暴露"存在但无权限"这种会泄露信息的提示）。

## 七、C端用户改为账号密码登录

暂时和微信解绑：`user` 表新增 `username`/`password`（MD5加密，和员工登录同一套 `DigestUtils.md5DigestAsHex` 方式）字段，`POST /user/user/login` 从原来的"传微信 code 换 openid"改成和管理端一样"传 username+password"。原有的微信 `openid`/`wxLogin()` 逻辑整体保留（`UserServiceImpl.wxLogin`没删，`UserController`里原来的微信登录方法注释掉留着），以后要接回真实微信登录时可以直接恢复，不用重写。

迁移脚本：`database/migration_user_account_login.sql`（已在本地库执行），`database/sky.sql` 同步更新了建表语句和种子数据。种子测试账号：`user1` / `123456`。

`customer.html` 顶部原来的"手动粘贴 token"输入框换成了真正的用户名+密码登录表单，登录成功后把接口返回的 token 存 localStorage，新增"退出"按钮清空 token。

## 八、customer.html 品类按钮 bug + 商户 dummy data

bug：点击顶部"餐饮/医药/蔬果/花卉"按钮时只调了 `loadShops()`，没有把视图切回商铺列表——如果当前正停留在店铺详情页，点按钮会看起来"没反应"（其实数据刷新了，只是详情页还盖在上面）。修复：点击按钮的事件里补上把 `#shopDetail` 隐藏、`#shopListView` 显示。

dummy data：之前只有商户1（蜀味阁）有完整菜单，商户2/3/4 基本是空的，商户3 还因为之前测试营业类型冷却逻辑被顺手改成了"花卉"（业务类型2→4），导致"医药"类目点开是空的。处理：
- 商户3 改回种子数据里原本设定的医药类型（`database/migration_dummy_shop_data.sql` 里有 UPDATE 语句重置 name/business_type/secondary_business_type/business_type_updated_at）。
- 给商户2（江南小厨，餐饮+蔬果）、商户3（健康药房，医药）、商户4（花语鲜花，花卉）各补了3个分类、6-7个菜品，医药类目里特意留了2个 `need_prescription=1` 的处方药条目用于前端"处方药"标签展示。
- 迁移脚本已在本地库执行，`database/sky.sql` 同步更新（分类id 24-32，菜品id 70-88），保证新装库也有这份数据。

## 九、customer.html 补全购物车 / 下单 / 地址簿

后端这几块接口（`/user/shoppingCart/*`、`/user/order/*`、`/user/addressBook/*`）其实早就存在，只是 `customer.html` 一直没接上，用户端只能浏览店铺看菜单，没法下单。补的东西：
- 每个菜品卡片加了"加入购物车"按钮，顶部加了"🛒 购物车"（角标显示件数）/"📍 我的地址"/"📦 我的订单"三个入口，统一用一个通用弹窗组件（`#modalOverlay`）承载，不搞三套独立面板。
- 购物车弹窗：加减数量、显示合计、"去结算"。
- 结算流程：选收货地址（没有地址就直接在弹窗里加）→ 提交订单 → 因为项目没有真实微信支付商户资质，`submitOrder()` 里提交完订单立刻紧接着调一次 `/user/order/payment`，把订单标记为已支付（后端 `payment()` 本来就是直接跳过真实微信支付、mock 成功，这里前端只是把这两步串起来，方便走完整条链路）。
- 地址簿：查看/新增/设默认/删除，编辑没做（够用，范围克制）。
- 我的订单：历史订单列表 + 状态文案 + 待付款/待接单状态下可取消。

**顺手修的一个后端 bug**：`OrderServiceImpl` 里管理端"拒单"、管理端"取消订单"、用户"取消订单"这三处，只要订单是已支付状态，都会调 `weChatPayUtil.refund()` 发起真实微信退款请求——但项目根本没有真实微信支付商户资质（`payment()` 那里就已经为了这个原因直接 mock 成功跳过了真实支付），所以只要用户走完"下单→(mock)支付→取消"这条路径，取消订单必现"未知错误"。改成和 `payment()` 一致的处理方式：跳过真实退款调用，直接把 `payStatus` 标记为已退款/记日志。三处都改了（`userCancelById`/`rejection`/`cancel`）。

## 十、8080端口被无关项目占用，后端改用8090

现象：`customer.html` 点开"我的地址"报 `Unexpected token '<', "<!doctype "... is not valid JSON`。排查发现不是我们项目的bug——8080端口被同一台机器上另一个完全无关的项目占用了（`C:/1111homework_for_master/90017/PTX-Cancer-Treatment-App/backend`，`com.titan.titan.TitanApplication`）。这个项目也在跑，抢占了8080，nginx转发过去的请求实际打到了它的Tomcat上，返回了它的默认404 HTML页面而不是我们后端的JSON，前端 `resp.json()` 解析HTML自然报错。

处理方式：没有杀那个无关项目的进程（避免影响别人/别的会话正在跑的东西），改成让本项目后端换端口，两边互不干扰：
- `backend/sky-server/src/main/resources/application.yml`：`server.port` 从 8080 改成 8090。
- `frontend/conf/nginx.conf`：`upstream webservers` 从 `127.0.0.1:8080` 改成 `127.0.0.1:8090`。
- `frontend/html/index.html` 里一处报错提示文案（"请确认后端已在 8080 端口启动"）同步改成 8090，纯文案，不影响功能。

以后本机上如果又出现"前端报JSON解析错误/看起来像别的网站的404页面"，先怀疑是不是端口被别的项目占了（`netstat -ano` 查端口对应PID，再用 `Get-CimInstance Win32_Process` 看那个PID的命令行/工作目录），不要一上来就当成自己代码的bug排查。

## 十一、拆分商家后台 / 系统管理员后台 + 4个老板账号

之前 `admin.html` 一个页面混着"店铺员工"和"平台超管"两种登录身份，登录提示里还得专门注明"平台超管登录了也没用，本页没做那部分UI"，比较别扭。这次按身份拆成两个页面：

- `admin.html` 改成纯粹的"商家管理后台"，登录提示只讲店铺员工/老板账号，不再提平台超管。
- 新增 `admin-system.html`："系统管理员后台"，只接 `platform` 这类平台超管账号，登录成功后先放一个占位页面（"具体管理功能还在规划中"），后续再加内容。

两个页面登录都还是走同一个 `/admin/employee/login` 接口——后端本来就已经支持这个区分（`employee.shop_id` 为 null 表示平台超管，JWT claim 里带不带 `shopId` 决定 `BaseContext.currentShopId` 是否为空），不需要改后端代码，纯前端拆分。

**新增4个店铺老板账号**（`database/migration_shop_boss_accounts.sql`，已在本地库执行，`sky.sql` 同步更新）：本质就是给对应店铺各加一个 `employee` 记录（跟 `admin`/`shop2emp` 是同类东西，只是名字上区分"老板"），登录后台后天然只能看到自己店的分类/菜品/套餐/订单，不需要额外权限代码：

| 用户名 | 密码 | 对应店铺 |
|---|---|---|
| boss1 | 123456 | 蜀味阁（店铺1） |
| boss2 | 123456 | 江南小厨（店铺2） |
| boss3 | 123456 | 健康药房（店铺3） |
| boss4 | 123456 | 花语鲜花（店铺4） |

顺手发现并修了个 `sky.sql` 里的老bug：`admin` 员工的种子密码写的是明文 `123456`，而登录逻辑是按 MD5 比对的，新装库的话 `admin` 账号根本登不进去（本地库里这行早就被后续操作改成正确的MD5值了，只是 `sky.sql` 文件本身一直没同步，这次一起改成 `e10adc3949ba59abbe56e057f20f883e`）。

## 十二、商家后台加"数据统计"模块

后端这块（`ReportController`营业额/订单/用户/销量Top10统计 + `ExportController`导出Excel）本来就有、而且本来就是按 `BaseContext.getCurrentShopId()` 做店铺隔离的，之前只是没在 `admin.html` 里接出来。这次加了个"数据统计"侧边栏页：
- 日期范围选择（默认最近7天）+ 查询按钮，四张表：营业额 / 订单统计（带总数/有效单/完成率汇总）/ 用户统计 / 销量Top10。
- "导出Excel（最近30天运营数据）"按钮：调 `/admin/export/export`（`fetch`+`token`头，因为这个后台是token放header不是cookie，不能直接用`<a href>`），拿到blob后用临时`<a download>`触发浏览器保存。

用 boss1 账号验证过：统计接口只返回店铺1自己的数据，导出的xlsx文件也能正常生成、下载。用户统计那张表的"累计用户"口径本来就是全平台维度（`user`表没有shop_id，见"已知限制"），这个不算bug。

## 十三、补全评价的提交/回复入口

后端评价相关接口（`/user/review/submit`、`/admin/review/page`、`/admin/review/reply`）都是多品类那一阶段就做好的，但两个前端页面都只接了"查看"，没接"提交"和"回复"，导致评价功能实际上是个死胡同——用户看得到别人的评价，自己却没地方评；商家更是完全看不到、也没法回。这次补上：

- `customer.html`"我的订单"里，状态为"已完成"的订单会多一个"评价"按钮，点开是内联的星级选择（1-5星点击）+ 文字评价 + 提交，提交成功后原地换成"评价已提交，感谢！"。没有做"我是否已经评价过"的预判——如果对同一订单重复点评价，后端会返回"评价已存在"，直接把这条错误消息弹出来，不算bug，只是没有额外做本地状态缓存去提前隐藏按钮（保持改动面小）。
- `admin.html` 新增"评价管理"侧边栏页：星级筛选 + 分页列表，每条评价没回复的话显示一个回复输入框+回复按钮，回复过的直接显示"商家回复：xxx"。跟其他管理接口一样自动按当前登录店铺隔离（平台超管看全平台，本页面目前给店铺老板用，未特别处理超管场景）。

用 user1 对一个已完成订单（shop4，订单20120）提交评价、boss4 登录看到并回复，成功之后再用 user1 查该店铺评价列表确认能看到商家回复——链路验证通过。

## 十四、新增发票功能

从零加的一个功能，之前完全没有。范围上参照现有的评价/AI客服这类"C端小功能"的做法：不接真实的税务开票接口（跟AI客服接OpenAI真实API不同，这个纯粹是数据记录+格式化展示，没有真实第三方服务可接，也没必要为了demo去接），只做"申请记录 + 生成一张格式化的电子发票页面"。

- 新表 `invoice`：`order_id`唯一（一单一票）、`user_id`、`shop_id`、抬头`title`、抬头类型`invoice_type`（1个人2单位）、`tax_number`（单位必填）、`email`（可选）、冗余存的订单金额`amount`。
- 后端 `/user/invoice/apply`：校验订单归属当前用户、订单必须是"已支付"状态（`payStatus=PAID`，不要求已完成——现实中付完款就能开票，不用等送到）、同一订单不能重复开票、单位抬头必须填税号。`/user/invoice/list`查自己的开票记录，`/user/invoice/{id}`查详情（校验归属）。
- `customer.html`"我的订单"里，`payStatus`为已支付的订单会出现"开发票"按钮（跟"评价"按钮同一套内联表单的路子），选个人/单位、填抬头（单位额外要求税号）、可选邮箱，提交后原地显示"发票已开具"+一个新标签页链接。
- 新增 `frontend/html/invoice.html`：单独一个"发票"页面，`?id=`取发票id，拉详情渲染成一张格式化电子发票（发票编号、抬头、销售方店铺名、订单号、金额等），带"打印/保存为PDF"按钮（`window.print()`，打印样式单独做了浅色适配，不是把深色主题直接打印出来）。

用 user1 对 shop4 一个已支付订单实测：申请发票成功、重复申请被正确拒绝（"该订单已申请过发票"）、对未支付订单申请被拒绝（"订单尚未支付"）、单位抬头不填税号被拒绝，详情接口和打印页面都能正确展示店铺名/订单号/金额等信息。

## 四、仍然保留的已知限制

- `employee.username` 全局唯一（原因见上）。
- 用户（`user`）表本身没有 `shop_id`，"新增用户数"这类统计口径继续是全平台维度，不区分店铺——这是"用户账号本来就是跨店铺共享的"这个既定设计决定的自然结果，不算是遗漏。
- 原来那个没有源码的 Vue 管理后台（旧 `frontend/`）已删除；`simplefrontend/` 改名为 `frontend/`（nginx.exe 及 conf 里全是相对路径，改名不用改配置），现在 `frontend/` 就是本项目唯一的前端，含 `index.html`（接口测试台）/`admin.html`（简化管理后台）/`customer.html`（用户浏览页）三个页面。
- 评价目前是"一单一评"（整单打分+评论），不支持对单个菜品单独评分，这是有意控制范围，不是遗漏。
- `admin.html` 当初按你选的范围（登录+菜品/分类/套餐/订单）做的，没有店铺管理界面，所以新建店铺、设置主/副营业类型目前只能直接调接口（`POST/PUT /admin/shop/platform`），没有对应的后台页面表单。
- C端用户登录目前是账号密码（见"七"），微信 `openid`/`wxLogin` 逻辑保留但未接入；真要接微信小程序/H5，把 `UserController` 里注释掉的微信登录方法恢复、前端换回 `code`→登录流程即可。
- AI 客服接口没有做调用频率限制，理论上一个账号可以疯狂刷接口刷出真实的 OpenAI API 费用，真要上线需要加限流（比如每用户每分钟几次）。
- AI 助手是"纯聊天"，不能查真实的菜单/订单数据，回答范围仅限于系统提示词里描述的"平台介绍/使用引导"这类通用问题。
