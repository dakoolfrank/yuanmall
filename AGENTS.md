# 团团园园杂货铺服务器执行约束

适用于 `/workspaces/yuanmall` 及其子项目。运行、部署和环境切换以 [DEPLOY.md](DEPLOY.md) 为准。

## 项目与来源

- 自有仓库：`https://github.com/dakoolfrank/yuanmall.git`。用户要求从自有仓库更新时，先检查 origin，不擅自下载官方上游替代自有代码。
- `ruoyi-vue-pro/` 是唯一 Java 业务后端，供新管理端和顾客商城共用。前端切换不能删除、替换或另建 Java 服务。
- 当前管理端为 `yudao-ui-admin-uniapp/`：一套源码，日常在服务器运行 H5，用浏览器调试；后续可在服务器编译微信管理小程序，再到本机微信工具验收、上传。
- 顾客购物端是 `yudao-mall-uniapp/`，与管理端不同。服务器 sparse checkout 仍排除它，不部署顾客购物 H5，不为管理端任务检出该目录。
- 2026-11-14 用户明确要求旧 Admin 不备份直接删除。旧 `yudao-ui-admin-vue3/`、旧专用 Vite 配置及 `/production/yuanmall/web/` 已删除；不得通过 Git restore 或旧部署脚本恢复旧前端。

## 当前运行方式

- 统一入口 `https://mall.nexrur.ai/`，前端使用 uni-app H5 路由；旧 `/admin/`、`/index`、`/login` 回到根入口。
- `/admin-api/` 是管理接口，`/app-api/` 是顾客小程序接口，均由 `ruoyi-vue-pro` 提供。
- 当前环境为 dev：`yuanmall-dev-backend.service`，Java profile `serverdev`，`127.0.0.1:48081`，MySQL `yuanmall_dev`，Redis DB 1。
- `yuanmall-dev-admin.service` 在新管理端目录运行 `uni -p h5 --mode serverdev`，只监听 `127.0.0.1:5173`，通过 Nginx/Tunnel 对外提供页面和 `wss://mall.nexrur.ai/__vite_hmr` 热更新。
- 保留 Vite 热更新，不能为提速自行改为静态生产发布。组件修改由 HMR 生效，Java 修改仍需打包和重启对应开发后端。
- 本机资源只运行一套商城环境，不同时启动开发/生产 Java 或两套 Admin。
- 浏览器与顾客小程序共用域名；请求都进入服务器当前环境，不按调用者自动区分 dev/prod。微信合法域名保持 `https://mall.nexrur.ai`，不填写内部端口。

## 数据、工具与边界

- 生产 Java、配置、MySQL 库 `yuanmall` 和 Redis DB 0 保留；生产 Admin 已移除。恢复生产后端不等于已有可运行的生产前端，不能再引用已删除的旧静态目录。
- 禁止在生产库重跑初始化 SQL、用开发库覆盖生产库或执行 Redis FLUSHALL。部署前端不需要初始化业务数据库。
- 管理端开发配置在 Git 忽略的 `yudao-ui-admin-uniapp/env/.env.serverdev.local`；后端本机配置在 `_local/serverdev/`。账号密码不预填、不写入前端配置或 Git。
- 使用隔离的 `/opt/node-v24.11.0-linux-x64` 和 `/opt/yuanmall-build-tools`；当前 pnpm 9.15.9 满足项目 engines。依赖按锁文件安装，不自动升级 uni-app、Vite 或全局工具。
- AGV 使用的全局 Node 18 / tsx 不得覆盖。保留 QCC、AGV、Cloudflare Tunnel、Nginx 其他站点、证书和续期。
- 服务器 H5 不启动微信开发者工具。当前不构建、上传或发布微信小程序；后续执行需按对应任务确认管理端自己的 AppID，不复用顾客小程序 AppID。
- `scripts/start-local.ps1` 等旧桌面体验脚本不是服务器的新 Admin 启动入口，禁止用其覆盖当前服务和路由。
- 不输出密码、Token、私钥或凭据；不执行未经授权的 Git 提交、推送或破坏其他未提交改动。
