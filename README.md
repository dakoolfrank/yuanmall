# yuanmall

商城项目总仓库，包含后端、管理后台和用户商城三套完整源码。使用一个 Git 仓库管理；后端和顾客商城沿用 Git subtree 管理，管理端来源见其 docs/UPSTREAM_YUANMALL.md。

```text
yuanmall/
├── ruoyi-vue-pro/          # Java 后端：原 yuanmall 仓库的全部代码
├── yudao-ui-admin-uniapp/  # 同一套管理端源码：H5 / 微信小程序
└── yudao-mall-uniapp/      # uni-app 用户商城
```

原后端的提交历史保留在本仓库中。三个项目的源码、配置和许可证分别保留在各自目录内。

## 腾讯云服务器环境约定

开发与部署操作先读 [AGENTS.md](AGENTS.md) 和 [DEPLOY.md](DEPLOY.md)：服务器只运行一套商城环境，对外统一使用 `https://mall.nexrur.ai`，本机分时切换 dev/prod 并隔离数据库与 Redis 逻辑库。同域名的微信开发工具、体验版和正式版都会随服务器切换环境，不能用内部端口区分外部调用者。

服务器运行管理端 H5 开发版，不部署顾客购物 H5，已通过 sparse checkout 排除 `yudao-mall-uniapp/`。下文的 Windows 本地体验流程不作为腾讯云服务器的环境切换入口。

## 打开工作区

用 VS Code 打开仓库外保存的 `yuanmall.code-workspace`。工作区名是 **yuanmall**，资源管理器直接显示三个并列项目，没有额外的 `yuanmall` 文件夹层级。

本机现有的 `D:\SouceFile\yuanmall.code-workspace` 也指向这三个项目。

## 历史 Windows 体验流程（不适用新管理端）

以下是旧桌面管理端的历史流程，不能用于启动当前 uni-app Admin 或覆盖服务器配置；当前部署以 DEPLOY.md 为准。

本机使用 Docker Desktop 的 Linux 容器运行 MySQL、Redis 和 Java 后端，Java 8 和 Maven 构建工具也在容器内。前端使用本机 Node.js、pnpm；准备脚本使用 Python 3。

在总仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
```

修改后端代码后，使用 `-Rebuild` 重新打包并重建后端容器；只启动后端环境可加 `-NoFrontends`。

| 服务 | 本机地址 |
| --- | --- |
| 管理后台 | http://127.0.0.1:5173 |
| 用户商城 H5 | http://127.0.0.1:5300 |
| 后端 API | http://127.0.0.1:48080 |
| MySQL | `127.0.0.1:13306`，数据库和应用用户名均为 `yuanmall` |
| Redis | `127.0.0.1:16379` |


数据库、Redis 密码自动生成并保存在被 Git 忽略的 `_local/.env`；日志和上传文件也保存在 `_local/`。支付使用模拟通道，文件使用本机存储，真实短信渠道关闭，定时任务暂不自动运行。

初始化使用基础库、Quartz、会员、支付、商城的 SQL。三份业务 SQL 及解压、修订副本均在 `ruoyi-vue-pro/sql/local/`，不随公开仓库分发；在新机器上启动前需要先准备好 `extracted/member.sql`、`extracted/pay.local.sql`、`extracted/mall.sql`。本机已准备好这些文件，支付导入副本省略了损坏的示例渠道配置。

停止本地服务并保留数据库：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1
```

## 上游对应关系

| 项目目录 | 上游仓库 | Git remote | 分支 |
| --- | --- | --- | --- |
| `ruoyi-vue-pro` | [YunaiV/ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) | `upstream-ruoyi-vue-pro` | `master` |
| `yudao-mall-uniapp` | [yudaocode/yudao-mall-uniapp](https://github.com/yudaocode/yudao-mall-uniapp) | `upstream-yudao-mall-uniapp` | `master` |

`origin` 仍是 `https://github.com/dakoolfrank/yuanmall.git`。初次导入版本记录在 `upstreams.json`，后续同步版本记录在 Git subtree 提交中。

### 新克隆后配置上游

普通 `git clone` 就能拿到三套源码。Git remote 配置属于本地设置，新克隆后在总仓库根目录执行一次：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-upstream.ps1 -SetupOnly
```

### 同步单个项目

先提交自己的改动，或用 `git stash` 收起改动，然后在总仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-upstream.ps1 -Project ruoyi-vue-pro
powershell -ExecutionPolicy Bypass -File .\scripts\sync-upstream.ps1 -Project yudao-mall-uniapp
```

每条命令只同步对应的目录，并产生 subtree 合并提交；有冲突时先解决当前合并，再继续同步其它项目。省略 `-Project` 会按顺序同步全部三个项目。

## 开发入口

- 后端：从 `ruoyi-vue-pro/` 打开 Maven 项目；当前分支要求 JDK 8，配置文件在 `ruoyi-vue-pro/yudao-server/src/main/resources/`。
- 管理后台：在 `yudao-ui-admin-uniapp/` 开发同一套 H5/微信源码。服务器实际运行 H5 `serverdev` 模式，配置、依赖安装和 systemd 命令见 DEPLOY.md。
- 用户商城：用 HBuilderX 打开 `yudao-mall-uniapp/`，按该项目 README 启动 H5 或小程序。
- Docker 部署说明移至 `ruoyi-vue-pro/script/docker-compose/README.md`，目录内部的相对路径保持有效。
- 总仓库的 GitHub Actions 已调整为从 `ruoyi-vue-pro/` 打包后端。各上游项目自带的工作流保留在对应项目内。

商城、会员、支付的业务数据库脚本仍需另行准备，导入前需匹配当前代码版本。本机专用文件可放在 `_local/`，本地 SQL 可放在 `ruoyi-vue-pro/sql/local/`，这两个目录均被总仓库忽略。
