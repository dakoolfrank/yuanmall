# 店主管理端源码来源

- 引入日期：2026-11-14（北京时间）。
- 上游：[yudaocode/yudao-ui-admin-uniapp](https://github.com/yudaocode/yudao-ui-admin-uniapp)。
- 分支：`master`。
- 固定提交：`99c5a98ff52f2b610560fdc694dfcbd906a172fa`。
- 协议：保留上游根目录的 `LICENSE`。
- 本机目录：`D:\SouceFile\yuanmall\yudao-ui-admin-uniapp`。
- 本项目属于 yuanmall 总仓库，目录内没有独立 `.git`。

本次以源码快照引入，保留上游模块供精简时核对。移除电脑管理端后，现有 `upstreams.json` 和 `scripts/sync-upstream.ps1` 只同步Java后端与客户商城；新管理端尚未登记 subtree 导入提交，不能直接假定已经支持 `subtree pull`。后续建立同步记录时，以本文件的固定提交为起点，保留本地改动。

本次GitHub清理先提交本说明与设计文档，完整源码保留在本机目录及本地 `archive/wechat-admin-source-20261114` 分支。大包推送未完成，因此不能把远端的文档目录当作可直接构建的完整工程；需要时可按上述固定提交从上游重新取得源码。

本地接入调整：

- `dev:mp-weixin` 和 `build:mp-weixin` 使用 `--mode wechat`，加载 `env/.env.wechat`。
- 管理接口使用 `https://mall.nexrur.ai/admin-api`，请求封装不再额外拼接此前缀。
- 微信 AppID 与默认登录账号、密码留空；实际配置放在已忽略的 `env/.env.wechat.local`。
- `prepare` 仅生成基础配置，避免上游的 `git init && husky` 在源码目录内创建嵌套仓库或修改总仓库 hooks。
- 移除 Vite 对整份环境配置的控制台输出。

此次只完成源码引入、环境入口和设计修订。尚未安装依赖、启动第二个编译进程、构建或上传店主管理小程序，也未切换服务器。

业务设计见 [DESIGN_ADMIN.md](./DESIGN_ADMIN.md)。
