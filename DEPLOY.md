# 团团园园杂货铺服务器部署

2026-11-14：管理端已改为自有 `dakoolfrank/yuanmall` 中的 `yudao-ui-admin-uniapp`，来源提交 `6bc0b4929e8478f5ed9db99a2906562906aa9eae`。旧 Web Admin 按用户要求直接删除，不备份。

## 对外入口与后端

| 地址 | 用途 |
| --- | --- |
| `https://mall.nexrur.ai/` | uni-app 管理端 H5，电脑/手机浏览器日常开发 |
| `/admin-api/` | 管理接口，由现有 `ruoyi-vue-pro` 提供 |
| `/app-api/` | 顾客小程序接口，同一个 Java 后端 |

公网经 Cloudflare HTTPS → Tunnel → 本机 Nginx 80。管理端页面代理到 `127.0.0.1:5173`，当前两类接口代理到开发后端 `127.0.0.1:48081`。旧 `/admin/`、`/index`、`/login` 重定向根入口。H5 使用 uni-app 页面路径和 hash 路由，不沿用旧桌面 Admin 的 `/index` 页面结构。

顾客 `yudao-mall-uniapp` 仍被 sparse checkout 排除，不部署购物 H5；这里运行的是管理端 H5。管理端同一套源码可编译微信管理小程序，但当前没有执行小程序上传发布。

## 当前运行矩阵

| 项目 | 当前 dev | 保留的生产后端 |
| --- | --- | --- |
| Java 服务 | `yuanmall-dev-backend.service` | `yuanmall.service`，停止/禁用 |
| Java 产物 | `/workspaces/yuanmall/ruoyi-vue-pro/yudao-server/target/yudao-server.jar` | `/production/yuanmall/yudao-server.jar` |
| profile / 端口 | `serverdev` / `127.0.0.1:48081` | `prod` / `127.0.0.1:48080` |
| MySQL | `yuanmall_dev` | `yuanmall` |
| Redis | DB 1 | DB 0 |
| Admin | `yudao-ui-admin-uniapp` H5 开发服务 / `127.0.0.1:5173` | 当前未部署；旧静态前端已删除 |

Java 仍是同一个 `ruoyi-vue-pro` 后端；前端迁移后，已为店铺配送、线下付款和单级分类更新开发 JAR，未重新初始化数据库。MySQL/Redis 沿用 `/production/yuanmall/infrastructure/compose.yaml` 的已有容器。前后端共用当前选择的业务环境；电脑、手机、顾客小程序的请求都会受到未来 dev/prod 切换影响。

## 新 Admin 的启动与热更新

源码：`/workspaces/yuanmall/yudao-ui-admin-uniapp`。服务：`yuanmall-dev-admin.service`，由 `yuanmall-development.target` 管理。

实际执行：

```bash
cd /workspaces/yuanmall/yudao-ui-admin-uniapp
SKIP_OPEN_DEVTOOLS=true NODE_OPTIONS=--max-old-space-size=1024 \
  /opt/node-v24.11.0-linux-x64/bin/node \
  node_modules/@dcloudio/vite-plugin-uni/bin/uni.js -p h5 --mode serverdev
```

服务已限制内存、使用 ubuntu 用户、只监听回环地址。服务器不安装或打开微信开发者工具。Node 24.11.0 与 pnpm 9.15.9 使用隔离工具目录，不替换 AGV 的全局 Node 18 / tsx。

依赖安装使用项目锁文件：

```bash
cd /workspaces/yuanmall/yudao-ui-admin-uniapp
PATH=/opt/node-v24.11.0-linux-x64/bin:/opt/yuanmall-build-tools/bin:$PATH \
NODE_OPTIONS=--max-old-space-size=768 \
pnpm --config.manage-package-manager-versions=false \
  --config.node-options=--max-old-space-size=768 install --frozen-lockfile
```

本机配置在 Git 忽略的 `env/.env.serverdev.local`：管理 API 使用 `https://mall.nexrur.ai/admin-api`，关闭前后端 API 加密以匹配现有后端，验证码开启，用户名/密码默认空。租户继续由后端的域名绑定和现有校验处理。

自提位置的 H5 地图选点使用高德，配置同在上述本机文件（不是 Java YAML）：当前 `VITE_PUBLIC_AMAP_KEY_JS` 为 Web 端（JS API）Key，`VITE_PUBLIC_AMAP_CS_JS` 为对应安全密钥，亦兼容 `VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_JSCODE`。在高德控制台将浏览器域名授权给 `mall.nexrur.ai`。实际值不提交、不输出；修改后执行 `sudo systemctl restart yuanmall-dev-admin.service` 使 Vite 重新读取 env，Java 后端无需重启。Web 服务 Key 本次不使用，保留在不以 `VITE_` 开头的 `AMAP_KEY_WS` 中，避免进入浏览器模块。地图选点按需加载，搜索地址或点击地图、确认并保存门店后自动记录 GCJ02 坐标，不手填经纬度、不用村庄中心代替实际位置。未配置或加载失败可继续保存停用草稿。

Vite `serverdev` 模式配置在项目 `vite.config.ts`：

- 只监听回环地址，固定 5173；公网 HMR 使用 `/__vite_hmr` 和 WSS 443。
- 预热登录入口，优化依赖的源码映射独立加载，保持应用源码 HMR。
- H5 明确解析 Wot UI / z-paging 组件，避免使用 UniComponents 替代 easycom 后组件不渲染；微信端 easycom 配置保留。
- 禁止 HTTP 读取 `.env`、本机 env 文件、证书和 Git 配置。
- H5 登录滑块补充鼠标事件；微信触摸操作保留，DOM 监听只在 H5 编译。

修改页面直接热更新；依赖或 Vite 配置变化会重新预编译。不要为日常 H5 调试运行微信构建或自动上传命令。

## Java 开发与环境边界

Java 源码保存不等于更新 JAR。修改后端时按现有开发流程先停止开发后端、打包工作区 JAR、再启动，不改生产 JAR。数据库迁移或生产发布另行处理，禁止复制开发库覆盖生产。

当前管理端服务与生产 Java 服务仍有互斥关系。旧生产前端 `/production/yuanmall/web/` 已删除，不能直接照旧文档恢复旧 release。未来切到 prod 时必须先准备新管理端与生产 API 的配套配置，处理同域名所有调用的影响后再执行环境切换。

## 维护与验收

```bash
systemctl status yuanmall-dev-admin yuanmall-dev-backend
journalctl -u yuanmall-dev-admin -n 60 --no-pager
sudo nginx -t
```

实际路由 `/etc/nginx/snippets/yuanmall-locations.conf` 当前使用开发代理；模板为 `yuanmall-dev-locations.conf`。更新前后检查根入口、登录及滑块、管理员权限、商品/订单只读接口和 WSS 热更新；不要只凭 HTTP 200 判断业务成功。

同时检查 AGV 直接 IP 验证接口与 QCC `/health`。这次管理端部署不改 QCC、AGV、Tunnel 连接、TLS 证书或续期配置。

旧 `yudao-ui-admin-vue3/` 及其专用 `_local/serverdev/vite.config.mts` 已删除；原部署说明涉及这些路径的部分属于历史状态。禁止用旧桌面一键脚本恢复该前端。提交、推送按用户单次授权执行。


## 2026-11-14 店铺自送与见面收款增量部署

开发后端已增加店铺配送/线下确认收款代码及增量迁移；仅当前开发库新增字段、权限、字典和微信寄件设置表，生产数据库与生产 JAR 未更新。开发配置显式开启免费店铺配送。接口状态与验收边界见 `ruoyi-vue-pro/docs/API_STORE_DELIVERY.md`。此前“Java 后端没有因前端更换而重启”是前端迁移阶段记录，本轮 Java 功能修改已按约定重新打包并重启开发后端。

构建前可回退的旧开发 JAR 在 `_local/serverdev/backups/offline-receipt-20261114/yudao-server.jar`。回退只停开发后端、替换工作区 JAR 再启动；新增表/列保留，不删除收款记录。回退后不要接收新版见面付款订单，须先核查未完成订单。

## 2026-11-15 无图、单级分类维护

开发后端已同步取消分类图片必填，新分类无图以空字符串保存；商品分类校验改为允许启用且无子分类的一级分类，旧子分类仍兼容。前端去掉父级和图片控件，新增 parentId 固定为 0；历史分类未自动迁移或删除。本轮无需数据库结构迁移，仅更新开发 JAR，生产 JAR/数据库不变。打包前开发 JAR 保留于 `_local/serverdev/backups/flat-category-20261115/yudao-server.jar`。


## 2026-11-15 实物商品与图片目录

当前开发库租户 1 配置六款实物商品（SPU 642–647），分类为零食饮品、生鲜调味；旧的五款商品和 41 个分类经业务接口逻辑删除。六款商品已补齐实物图并全部上架；最初仅启用免费店铺配送，2026-11-16 已按用户要求统一支持自提和店铺配送（`deliveryTypes=[2,3]`），不引用错误快递模板。价格库存及日期原始清单、导入记录、图片同步工具保存在忽略目录 `_local/serverdev/catalog-20261115/`；实际库存随后续下单变化，不以原导入数量覆盖。

照片由店主放入 `/production/yuanmall/uploads/products/` 下的六个编号目录，现有主图均为各自目录的 `cover.jpg`；公开地址 `/mall-assets/products/` 仅静态访问这一目录，关闭目录列表和写入请求，配置在 `yuanmall-assets-locations.conf`，保留既有历史图片代理。公共目录不放凭据或内部业务清单。后续补图使用本机 `sync_images.py` 先预览、加 `--apply` 才同步主图和轮播；该操作不改变商品上下架。

替换前目录数据库快照：`_local/serverdev/backups/catalog-20261115-220647/catalog-before.sql`；私有权限保存，未改生产库或生产 JAR。

## 2026-11-16 管理员登记线下已付款

订单卡片和详情提供“已付款”人工登记入口。后端允许店铺配送、见面付款订单在待配送或配送中登记，保留权限、全额金额、重复提交和租户校验；登记不改变配送状态，不调用微信支付。前端使用 Vite 热更新，开发后端已重新打包并启动，9 项店铺配送/收款单元测试通过。本轮无数据库结构迁移，未修改已有订单的实际付款记录。

更新前开发 JAR 和订单记录快照保存在 `_local/serverdev/backups/manual-paid-20261116-073038/`。回退时仅停止开发后端、恢复该目录的 JAR 到工作区运行路径并重新启动；前端若回退，同步恢复本轮按钮及入口改动，保留后续业务数据。

## 当前导航与历史数据

店主首页显示商品、订单、售后、自提；物流和收款入口已隐藏，原设置页面与配置保留。自提入口复用现有自提门店页面与权限，正式自提点须填入真实联系和定位信息后启用。商品类别在商品列表内维护。订单和售后时间统一按北京时间显示，不跟随浏览器时区。

2026-11-15 按用户要求清理当前开发库租户 1 的历史订单 237 笔、售后 26 笔及关联明细和日志，采用逻辑删除；支付记录、商品库存和经营配置保留。恢复备份在 `_local/serverdev/backups/clear-orders-20261115-224658/`。这是一次性历史数据整理，不能作为部署初始化步骤重跑，也不能清理随后产生的新订单。

## 2026-11-16 自提点待完善保存

开发库应用增量迁移 `ruoyi-vue-pro/sql/mysql/migrations/20261116_pickup_store_drafts.sql`，仅将自提门店的营业时间和经纬度列改为可空，不改旧门店数据；开发后端已打包重启，4 项草稿校验测试和 9 项配送/收款测试通过。启用门店仍必须有联系电话、营业时间和地图位置，停用草稿不进入顾客可选列表。

H5 地图选点已接入高德，模拟交互检查通过，测试坐标提交已拦截，真实门店坐标保持未设置。初次真实网络检查遇到 `INVALID_USER_DOMAIN`；2026-11-16 更新高德域名白名单后，普通页面和弹窗搜索均返回 `OK`，来源仍为 `https://mall.nexrur.ai/`，可搜到北法信村店候选，真实 SDK 的选点确认及消息回传通过。历史授权故障已解决，不再作为当前阻塞。检查结果分别保存在本机 `_local/serverdev/pickup-map-ui-verification.json` 与 `_local/serverdev/amap-real-picker-verification.json`；真实门店位置仍需店主确认并保存。

实际 Admin 页面已使用真实高德 SDK 验证搜索、选点确认及回填，并拦截保存请求核对经纬度参数；正式门店数据未改变。联调结果为 `_local/serverdev/amap-admin-live-verification.json`。这些检查不会代替店主保存并启用自提点。

本机租户 1 的旧测试门店“ee”“阿里小店”“测试自提点”（ID 4、2、1）已按用户要求于 2026-11-16 通过管理接口逻辑删除，管理列表仅保留正式自提点（ID 5）。正式点最初保存为待完善草稿，现已由店主填写实际联系方式、营业时间和定位并启用。部署前 JAR、门店表与订单快照保存在 `_local/serverdev/backups/pickup-draft-20261116-222131/`；回退开发 JAR 时保留可空列和草稿数据。生产库与生产 JAR 未修改。

正式点已通过管理接口绑定现有 `admin`（本租户用户 ID 1）为取货负责人。详情页显示真实负责人，不再展示“绑定核销员”按钮和员工选择器；确认取货仍经过原后端的权限及门店绑定校验，不改变已付款登记流程。绑定后的门店资料保持原值，页面检查和精简门店权限列表验证结果在 `_local/serverdev/pickup-owner-result.json`。本轮仅前端热更新和当前开发库负责人关联，无需重启 Java。

## 2026-11-16 所有商品统一支持配送和自提

当前六款商品（SPU 642–647）的配送配置已统一为接口值 `[2,3]`，数据库逗号分隔值为 `2,3`。商品编辑页不再提供逐商品勾选或运费模板选择，直接显示“店铺配送 / 到店自提”；当前管理端新增、编辑提交均固定携带这两种方式。顾客下单时仍选择本次配送还是自提，后端原配送匹配及门店有效性校验继续生效。

本次在事务内锁定这六条商品及其 SKU，仅修改商品配送配置，不通过全量商品更新接口回写旧库存。已比较前后完整记录，价格、库存、图片等字段和全部 SKU 行保持原值。变更前后快照及只回退配送标记的 SQL 在 `_local/serverdev/backups/all-products-pickup-20261116-232839/`，结果指针为 `_local/serverdev/all-products-pickup-result.json`。未初始化数据库、未改历史订单、未重启 Java；切勿用旧快照覆盖后来订单产生的库存变化。

管理接口已核对六款商品均返回 `[2,3]`；实际浏览器已验证新增/编辑页显示统一取货方式、无逐商品勾选，编辑保存请求（拦截写入）携带 `[2,3]` 且价格、库存和介绍图片参数保留。结果在 `_local/serverdev/all-products-pickup-ui-verification.json`。此项是配置与 Admin 提交检查，未创建顾客订单。

## 自提见面付款增量部署

已补齐 `deliveryType=2` + `paymentMethod=2` 的普通订单：结算与创建允许自提见面付款；确认收款接口支持自提状态 10，登记后保持状态 10；已付款且绑定门店的管理员可通过原自提接口确认已取货，状态转为 30。收款和取货均使用当前租户订单行锁，保留金额、方式、退款状态、重复操作和权限校验。店铺配送开始/送达接口仍只接受方式 3，不把自提当成配送。详见 `ruoyi-vue-pro/docs/API_STORE_DELIVERY.md`。

已按单环境约束停止开发后端、执行测试与打包、再启动。33 项测试全部通过：新增自提流程 11 项，原配送收款 9 项，在线支付回调 9 项，门店草稿 4 项。备份与构建日志为 `_local/serverdev/backups/offline-pickup-20261116-235903/`，部署指针为 `_local/serverdev/offline-pickup-current.json`；开发 JAR SHA-256 为 `c50437b4de633aced9d853bac5004c0a7ce58088cc488b1da70267ff1a8da5a2`。生产 JAR 校验一致，部署时核对原有订单的状态、金额和收款信息均未变，无数据库迁移。

运行中接口实测自提/配送见面付款结算均成功，饼干商品应付 1000 分、运费 0，仅执行只读请求；Admin 收款、取货浏览器检查采用拦截请求，真实订单未登记收款或取货。报告为本机 `_local/serverdev/offline-pickup-live-settlement.json`、`_local/serverdev/offline-pickup-ui-verification.json`。顾客端 0.1.0 无需为此修复重新上传；管理端继续 Vite 热更新。回退前先处理或核对新增的自提见面付款订单，不恢复旧订单快照覆盖业务数据。

## 2026-11-17 见面付款配送状态显示

管理端列表、详情对未结束的见面付款店铺配送订单，将收款登记状态作为主标签：待收款 / 已付款；配送进度单独显示待配送 / 配送中 / 已送达。列表保留独立“已付款”登记按钮。后台状态 10、20 的含义和实际付款记录未变，允许先送后收款；已完成、取消、在线支付及自提流程保持原有判断。本次仅 Vite 热更新，无 JAR、数据库或运行配置修改。

浏览器已验证待配送/配送中未登记收款、登记后、完成、取消、自提及在线支付共 9 种组合，主标签和配送进度正确，未收款仍可开始配送。验证禁止订单写入，核对真实订单付款、状态和金额未变；报告为本机 `_local/serverdev/collection-status-ui-verification.json`。

## 2026-11-17 顾客订单同时支持付款和交货分组

顾客待付款按 `payStatus=false` 且实际状态 0、10、20 查询；待发货／待取货按状态 10、待收货按状态 20 查询。同一见面付款订单可同时计入付款与交货入口，列表与角标使用同一条件，全部订单仍按订单去重。列表新增付款标记 `payStatus`，保留真实 `status`，兼容现有查询参数与计数字段。已完成和取消订单不计入待付款。未更改真实订单金额、付款或履约状态，无数据库迁移。

开发后端构建包含 39 项通过的测试（分组实际 SQL、分页与计数、用户和逻辑删除范围、收款后的分组变化，以及此前收款、自提和在线支付回归）。本次备份与构建结果为 `_local/serverdev/backups/order-groups-20261117-062832/`，指针为 `_local/serverdev/order-groups-current.json`。生产 JAR 不随本次开发部署更新，Admin 继续 Vite 热更新；顾客端未在本机构建或上传。

开发后端已启动，当前 JAR SHA-256 为 `3948ffdbdee6bf0e867c112eb92bf62359fdb005583cad7d6d2565f9f024e495`。本地和 `https://mall.nexrur.ai` 使用现有顾客会话做只读验收：账号 298 的全部订单 2、待付款 2、待发货 2，两个分组均包含订单 284、286；计数、列表、原状态和金额与数据库一致。报告为 `_local/serverdev/order-groups-live-verification.json`，未创建订单、登记收款或交货。公网测试使用浏览器 User-Agent；默认 Python User-Agent 的请求曾返回 403，未因此修改 Cloudflare 或运行配置。

## 2026-11-19 会员列表精简与旧会员清理

Admin 首页增加“会员”入口，沿用原会员查询权限。列表仅保留姓名/昵称、已绑定手机号、备注及受订单查询权限控制的“查看订单”，支持昵称、手机号搜索；移除列表上的积分、成长值、等级、批量发券和通用高级筛选。直接查看会员订单不访问或创建钱包。仅前端 HMR 更新，不重新打包或重启 Java。

按店主确认的明确范围，开发库租户 1 保留会员 ID 298、299、300、301，其余本次核对的 41 名旧会员停用并逻辑删除。事务执行前锁定对应会员，验证旧会员注册早于本次部署、无可见订单及有效访问/刷新会话；更新行数严格限定为 41。四名保留会员的完整资料、全部订单及相关钱包前后快照一致，未清除历史订单或钱包记录，生产库未操作。

本机私有备份目录为 `_local/serverdev/backups/member-cleanup-20261119-194914/`，结果指针为 `_local/serverdev/member-cleanup-result.json`。目录包含会员、订单、钱包前后快照和仅恢复会员状态、删除标记及原更新时间的 `restore-member-flags.sql`；恢复前检查后续会员活动，不将整份旧业务快照覆盖现有数据。快照包含个人资料和密码散列，仅留本机私有目录，不写入 Git。

浏览器只读检查已验证列表仅返回这 4 名会员、昵称搜索与清空恢复、手机宽度无横向溢出，以及“查看订单”按会员 ID 301 查询其订单。检查期间拦截所有非 GET 管理请求，确认没有调用钱包创建接口；记录为 `_local/serverdev/simple-members-ui-verification.json`。修改的 Vue 文件 ESLint 通过，运行中的 Vite 编译正常。

会员详情随后精简为资料、订单、售后三个页签，移除无关页签和“更多”，仅资料页显示编辑。订单卡片明确显示付款与交货进度，店铺配送不再留空，订单及售后时间统一使用北京时间；搜索保留订单号或售后商品名，并锁定当前会员。资料与所有明细页不再读取或创建钱包。现有编辑、订单处理、售后接口及业务记录保持原用途。

真实浏览器只读检查通过：三页签、资料编辑入口、订单搜索、售后查询、金额和北京时间、手机布局及无钱包请求；结果为 `_local/serverdev/simple-member-detail-ui-verification.json`。五个修改的 Vue 文件 ESLint 与 Vite 编译检查通过。本次无 Java 构建、服务重启、数据库或收款操作。

## 2026-11-19 修复旧权限缓存隐藏收款按钮

订单列表、详情挂载时同步当前服务器权限，收款自动弹层等待权限与订单一起就绪。保留独立收款权限和原后台校验，未修改角色授权、订单付款信息或服务配置。修复通过当前 Vite 热更新生效，无 Java 构建或服务重启。

实际浏览器检查覆盖旧权限缓存恢复、服务器撤销权限后的隐藏、配送前及配送中按钮、自动收款弹层、方式与到账勾选、二次确认、在线订单不出现手动收款及手机布局。收款提交仅针对拦截的虚拟订单，真实订单 288、289 状态、金额、收款信息前后一致；报告为 `_local/serverdev/collection-permission-refresh-ui-verification.json`。两个修改的 Vue 文件 ESLint 和 Vite 编译检查通过。

## 2026-11-19 普通见面订单完成前可取消

保持原 `DELETE /app-api/trade/order/cancel?id=...`：普通店铺配送、自提的见面订单在状态 10/20，无论是否收款都允许本人取消，结束状态拒绝重复操作。锁内重新校验归属及状态，与收款和交货完成串行，库存/营销后置处理和取消日志只处理一次，失败回滚。取消不清空付款或退款历史，不办理或伪造微信退款；已收款取消后的线下退款由店主另行处理。见面订单只由 Admin 完成，未收款不能完成，顾客收货与自动收货入口拒绝完成见面订单。

按单环境约束停止开发后端后执行测试及打包，再启动开发服务。76 项测试通过，其中新增 37 项真实 H2 SQL、租户拦截、事务、行锁、订单日志及多线程竞争集成测试；原在线支付、收款、自提、分组及门店草稿回归也通过。最终开发 JAR SHA-256 为 `2700fdbf86f3d92b9c1a0efcdbae6c499631a2372fbd646f291295c53b9f15c3`。

最终构建的备份、日志及结果在 `_local/serverdev/backups/offline-cancel-20261119-230646/`，指针为 `_local/serverdev/offline-cancel-current.json`。原取消限制版本的开发 JAR 保存在此前 `_local/serverdev/backups/offline-cancel-20261119-225942/`。回退前应对齐顾客端允许取消的规则，不恢复订单或库存快照覆盖后来业务。

前后快照核对：原有订单、SKU/SPU 库存、钱包、历史付款退款字段、订单日志一致；未运行真实订单取消、收款或完成请求。生产 JAR 保持原校验值，无数据库迁移。本轮顾客会话已过期，因此运行验证限于构建/进程类校验及可公开读取接口，不将隔离测试当成公网真实取消验收。顾客小程序编译上传由前端工作区负责。

## 2026-11-20 已取消订单的线下退款登记

开发环境已部署 `PUT /admin-api/trade/order/confirm-offline-refund`，使用独立权限，只给现有超级管理员角色授权。仅已取消、已收款、无支付单和既有退款的普通见面配送/自提订单支持全额登记；服务端核对金额、方式及租户行锁，登记与审计同时提交。保留订单取消及原收款历史，更新实际退款汇总，不调微信退款、不再次返库存。

迁移脚本 `ruoyi-vue-pro/sql/mysql/migrations/20261120_offline_refund_registration.sql` 只应用到 yuanmall_dev，新增四个可空列和按钮权限。执行前检查是否存在；ALTER 不重复运行。只清除开发 Redis DB 1 中新权限对应的缓存键，无全库清理。生产库、生产 JAR、Nginx、Tunnel、AGV 和 QCC 未修改。

102 项后端测试通过（新增 26 项退款集成用例）。开发 JAR SHA-256 为 `c37a9b0eb7de8c55f906f2b143b0877444a983e4986b92648fd5d40a5c9e5d38`。备份、日志及业务快照在 `_local/serverdev/backups/offline-refund-20261120-070255/`，指针 `_local/serverdev/offline-refund-current.json`。原有订单、库存、钱包和日志快照一致，没有实际退款登记。回退时保留新列及真实退款记录，禁止恢复旧业务快照覆盖后来操作。

Admin 通过 Vite HMR 生效：列表右上角“已取消”，下方显示未付款、待退款或已退款；详情左侧“确认已退款”、右侧备注。必须选择方式、确认已实际退钱及二次确认，不自动转账。五个文件 ESLint 和浏览器手机/电脑模拟流程通过；虚拟订单的写请求全部拦截，未改真实订单。顾客端不在本机构建或上传，新字段和调用边界见 `ruoyi-vue-pro/docs/API_STORE_DELIVERY.md`。

## 2026-11-20 Admin 订单详情精简

`yudao-ui-admin-uniapp/src/pages-mall/trade/order/detail/index.vue` 通过 Vite HMR 更新，默认只展示状态/金额、商品、对应配送或自提信息，以及有内容的留言备注。零元费用和优惠、默认规格、空字段隐藏；收退款和历史日志合并到默认折叠的“操作记录”，信息仍保留。业务操作规则保持原样，备注统一放右侧。

详情文件 ESLint、电脑及 375/320px 手机浏览器布局、7 种状态按钮和模拟收退款回归通过。报告 `_local/serverdev/simple-order-detail-ui-verification.json` 与 `_local/serverdev/simple-order-refund-regression.json`。本轮仅前端展示调整，无 Java 构建/重启、数据库迁移、真实订单写入或微信小程序构建上传，未改 Nginx、Tunnel、QCC 和 AGV。

## 2026-11-20 隐藏售后导航

首页只展示商品、订单、会员、设置；原“自提”入口改名为“设置”，当前内容为自提门店管理，沿用原路由及权限。会员详情只展示资料、订单，仍遵循原访问权限。只移除这两处售后导航及会员售后组件导入，售后历史、独立页面与后端接口保留；订单内的取消、线下退款入口保留。两个 Vue 文件 ESLint 及浏览器检查通过，记录 `_local/serverdev/hide-after-sale-ui-verification.json`。Vite HMR 已生效，无 Java 重启或数据库修改。

## 2026-11-20 店铺配送先配送、再收款、再确认

前后端统一为状态10仅开始配送，20未收款才登记收款，20已收款才确认送达；自提仍在10收款后确认取货。详情、订单列表与会员订单的主标签按交货进度展示，付款独立显示。旧提前收款逻辑可追溯至 `bc10bd30cd`，本轮不回写历史已收款订单。

仅更新当前开发前端及后端，无数据库迁移。四个前端文件 ESLint、107项后台测试、浏览器三步流程和7种状态布局通过。开发 JAR SHA-256 为 `819edced51348b722b636f6a5e590966b63d51b9456261d59f016bc36de6c0c4`；备份 `_local/serverdev/backups/dispatch-first-20261120-100934/`，指针 `_local/serverdev/dispatch-first-current.json`。现有订单、付款、退款、库存、钱包及日志快照一致，生产 JAR、Nginx、Tunnel、AGV和QCC保留原状态。未对真实订单执行配送、收款或完成。报告 `_local/serverdev/dispatch-first-workflow-ui-verification.json`、`dispatch-first-layout-verification.json`、`dispatch-first-deployed-build.json`；回退不得恢复旧业务快照。

## 2026-11-20 订单卡片状态去重

订单列表通过 Vite HMR 更新：右上角原状态标签保留，说明行去掉与标签重复的状态，保留其余配送、付款和退款信息。仅前端显示调整，无 Java 重启、数据库修改或真实订单操作。单文件 ESLint、浏览器13种虚拟状态、卡片跳转及手机布局检查通过，报告 `_local/serverdev/order-card-no-duplicate-ui-verification.json`。

## 2026-11-20 会员订单隐藏固定付款方式及重复状态

仅更新会员详情的订单组件：配送状态不重复展示，隐藏“见面付款”，保留实际收退款状态及历史在线支付方式，空付款行隐藏。单文件 ESLint、10种模拟订单、固定会员查询、详情跳转及手机布局检查通过，报告 `_local/serverdev/member-orders-compact-ui-verification.json`。Vite HMR 生效，无 Java 重启、数据库修改或真实订单操作。

## 2026-11-20 订单详情固定文案及重复状态精简

订单详情隐藏“见面付款”字样；配送信息区不再重复顶部已有的待配送、配送中等状态。实际收退款状态、联系人地址及全部业务按钮保留，历史在线付款方式仍显示。单文件 ESLint、7种状态及375/320px浏览器检查通过，报告 `_local/serverdev/detail-no-duplicate-ui-verification.json`。仅 Vite HMR 展示更新，无 Java 重启、数据库修改或真实订单操作。
