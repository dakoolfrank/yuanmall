# 园园杂货铺 · yuanmall

自营微信商城，本机工作区包含以下三个项目。当前GitHub保存Java后端与客户商城源码，以及新店主管理端的设计/来源记录；店主管理端完整源码已在本机引入，本次清理先不上传其大体积源码包。

| 目录 | 用途 |
| --- | --- |
| `ruoyi-vue-pro` | Java后端，部署于mall服务器 |
| `yudao-mall-uniapp` | 客户微信小程序：选购、支付、查看订单 |
| `yudao-ui-admin-uniapp` | 店主微信小程序：管理商品、订单、发货与售后 |

2026-11-14按用户要求清理本机与本仓库的电脑管理前端、启动/部署工具和上游同步配置。服务器由服务器侧独立维护，本轮不修改其服务或配置。新店主管理端已引入源码和微信配置，尚待构建、真机及业务联调；源码引入不表示已经可以替代全部运营操作。

## 工作区与设计

本机使用 `D:\SouceFile\yuanmall.code-workspace`，包含上述三个项目。源码子目录内不创建独立 `.git`。

- [客户商城设计](yudao-mall-uniapp/docs/DESIGN_YUANMALL.md)
- [店主管理设计](yudao-ui-admin-uniapp/docs/DESIGN_ADMIN.md)
- [后端设计](ruoyi-vue-pro/docs/DESIGN_BACKEND.md)

## 日常开发

Java、MySQL和Redis运行在mall服务器。本机使用VS Code、Node.js、pnpm和微信开发者工具，按当前任务启动所需编译监听，不启动H5或电脑管理服务。

```powershell
# 客户端
pnpm --dir yudao-mall-uniapp dev:mp-weixin
# 店主端，需要先配置独立AppID并安装依赖
pnpm --dir yudao-ui-admin-uniapp dev:mp-weixin
```

| 项目 | 微信开发产物 | 发布构建产物 |
| --- | --- | --- |
| 客户端 | `yudao-mall-uniapp/unpackage/dev/mp-weixin` | `yudao-mall-uniapp/unpackage/build/mp-weixin` |
| 店主端 | `yudao-ui-admin-uniapp/dist/dev/mp-weixin` | `yudao-ui-admin-uniapp/dist/build/mp-weixin` |

发布构建使用各项目的 `build:mp-weixin`。两个小程序分别使用各自AppID和代码包；上传、审核和正式发布按开发约定单独进行。

## 服务器入口

- `https://mall.nexrur.ai/`：当前服务器的页面入口，由服务器侧独立维护。
- `/app-api/`：客户小程序接口与会员认证。
- `/admin-api/`：店主管理接口与管理员权限。

两类API由同一Java服务提供。服务器分时运行dev/prod，同域名的开发工具、体验版和正式版都会随服务器环境切换；数据库与Redis逻辑库分别隔离。服务器操作另行授权，并先读取服务器上的 `AGENTS.md` 和 `DEPLOY.md`。

## 备用本机Java环境

仅在明确需要本机Java调试时使用，不作为服务器的环境切换入口：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
# 修改Java后需要重建时添加 -Rebuild
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1
```

启动脚本只管理Java、MySQL和Redis，保留数据卷，不启动小程序或其他前端。API监听 `127.0.0.1:48080`，MySQL为 `127.0.0.1:13306`，Redis为 `127.0.0.1:16379`。私有凭据、日志与上传文件保存在已忽略的 `_local/`；启动前需要准备 `ruoyi-vue-pro/sql/local/extracted/` 中匹配版本的会员、支付和商城SQL，不随公开仓库分发，不对已有环境重复初始化。

## 上游同步

| 项目 | 上游 | 本地remote |
| --- | --- | --- |
| Java后端 | [YunaiV/ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) | `upstream-ruoyi-vue-pro` |
| 客户商城 | [yudaocode/yudao-mall-uniapp](https://github.com/yudaocode/yudao-mall-uniapp) | `upstream-yudao-mall-uniapp` |
| 店主管理 | [yudaocode/yudao-ui-admin-uniapp](https://github.com/yudaocode/yudao-ui-admin-uniapp) | 当前为固定提交快照，见[来源记录](yudao-ui-admin-uniapp/docs/UPSTREAM_YUANMALL.md) |

`origin` 为 `https://github.com/dakoolfrank/yuanmall.git`。现有 `upstreams.json` 和脚本只管理Java与客户商城两个subtree项目：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-upstream.ps1 -SetupOnly
powershell -ExecutionPolicy Bypass -File .\scripts\sync-upstream.ps1 -Project ruoyi-vue-pro
powershell -ExecutionPolicy Bypass -File .\scripts\sync-upstream.ps1 -Project yudao-mall-uniapp
```

同步前提交或妥善保存当前改动。店主管理端尚未登记subtree导入提交，不直接套用自动pull；不要重新添加已移除电脑管理端的连接。
