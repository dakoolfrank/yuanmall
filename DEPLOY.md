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

当前开发库租户 1 配置六款实物商品（SPU 642–647），分类为零食饮品、生鲜调味；旧的五款商品和 41 个分类经业务接口逻辑删除。六款新商品已补齐实物图并全部上架，只启用免费店铺配送，不引用错误快递模板。价格库存及日期原始清单、导入记录、图片同步工具保存在忽略目录 `_local/serverdev/catalog-20261115/`；实际库存随后续下单变化，不以原导入数量覆盖。

照片由店主放入 `/production/yuanmall/uploads/products/` 下的六个编号目录，现有主图均为各自目录的 `cover.jpg`；公开地址 `/mall-assets/products/` 仅静态访问这一目录，关闭目录列表和写入请求，配置在 `yuanmall-assets-locations.conf`，保留既有历史图片代理。公共目录不放凭据或内部业务清单。后续补图使用本机 `sync_images.py` 先预览、加 `--apply` 才同步主图和轮播；该操作不改变商品上下架。

替换前目录数据库快照：`_local/serverdev/backups/catalog-20261115-220647/catalog-before.sql`；私有权限保存，未改生产库或生产 JAR。

## 2026-11-16 管理员登记线下已付款

订单卡片和详情提供“已付款”人工登记入口。后端允许店铺配送、见面付款订单在待配送或配送中登记，保留权限、全额金额、重复提交和租户校验；登记不改变配送状态，不调用微信支付。前端使用 Vite 热更新，开发后端已重新打包并启动，9 项店铺配送/收款单元测试通过。本轮无数据库结构迁移，未修改已有订单的实际付款记录。

更新前开发 JAR 和订单记录快照保存在 `_local/serverdev/backups/manual-paid-20261116-073038/`。回退时仅停止开发后端、恢复该目录的 JAR 到工作区运行路径并重新启动；前端若回退，同步恢复本轮按钮及入口改动，保留后续业务数据。

## 当前导航与历史数据

店主首页只显示商品、订单、售后；物流和收款入口已隐藏，原设置页面与配置保留。商品类别在商品列表内维护。订单和售后时间统一按北京时间显示，不跟随浏览器时区。

2026-11-15 按用户要求清理当前开发库租户 1 的历史订单 237 笔、售后 26 笔及关联明细和日志，采用逻辑删除；支付记录、商品库存和经营配置保留。恢复备份在 `_local/serverdev/backups/clear-orders-20261115-224658/`。这是一次性历史数据整理，不能作为部署初始化步骤重跑，也不能清理随后产生的新订单。
