# 店主管理小程序：本机测试与上传

更新：2026-11-21。管理端界面及微信项目标题使用“团团园园后台管理”，与顾客端“团团园园杂货铺”区分。

## 本机同步

本机已按目录合入本项目 GitHub 的 `d018be11c3af2b207487eecb1c65b457ffa54ca1` 管理端源码，包含服务器开发的商品、订单、会员和设置页面。仅同步 `yudao-ui-admin-uniapp`，没有切换整个仓库提交，没有覆盖客户小程序和 Java 的本机改动。

商品详情保留本机已有的“编辑／上架或下架／回收”，回收后可恢复，不在详情直接永久删除。保留权限检查及重复点击保护，并修复微信 WXSS 不接受的空标签伪类选择器。Logo 复用顾客端已有的同款 512 像素图片，避免两张原始大图使主包超限。

覆盖前的文件和差异备份在根目录 `_local/backups/admin-sync-20261120T140202642Z/`。本机验证日志保存在 `_local/admin-sync/`，均不提交。代码同步不表示已提交、推送或上传微信。

## AppID 与接口

- 店主管理 AppID：`wx358124e04ae74fee`，写入已忽略的 `env/.env.wechat.local`。
- 顾客商城 AppID 是另一个账号，不可混用。
- 编译命令使用 `wechat` 模式，接口为 `https://mall.nexrur.ai/admin-api`。
- 仅需要 AppID，不需要把 AppSecret 或代码上传私钥放到前端。
- 新小程序在微信公众平台单独配置 `request`、`uploadFile`、`downloadFile` 合法域名：`https://mall.nexrur.ai`。这里不带 `/admin-api`。

管理端使用管理员账号和密码，并按服务器要求完成滑块验证码。不会自动填入默认账号密码；会员微信登录不提供管理权限。

登录先按小程序 AppID 查询店铺绑定；尚未绑定时，仅微信端可使用 `env/.env.wechat` 明确配置的默认店铺。服务器仍校验账号、密码及权限。网络失败不绕过查询；H5 未绑定域名仍阻止登录。

## 编译与打开

在仓库根目录运行：

```powershell
# 只启动当前需要的编译，避免两个小程序监听同时占用内存。
pnpm --dir yudao-ui-admin-uniapp dev:mp-weixin

# 上传前使用独立发布产物。
$env:SKIP_OPEN_DEVTOOLS = 'true'
pnpm --dir yudao-ui-admin-uniapp build:mp-weixin
pnpm --dir yudao-ui-admin-uniapp check:mp-weixin-entry
```

微信开发者工具导入目录：

| 用途 | 目录 |
| --- | --- |
| 开发监听 | `D:\SouceFile\yuanmall\yudao-ui-admin-uniapp\dist\dev\mp-weixin` |
| 发布检查与上传 | `D:\SouceFile\yuanmall\yudao-ui-admin-uniapp\dist\build\mp-weixin` |

本机安装的 pnpm 为 9.15.9；若自动下载工程声明的包管理器失败，可在 `pnpm` 后加 `--config.manage-package-manager-versions=false`，依赖安装仍使用 `--frozen-lockfile`。

## 手机验收

1. 用管理员／开发者微信扫描本轮生成的 Admin 预览码；不能使用旧顾客端预览码。
2. 自行输入现有管理员账号密码，完成验证码；检查首页商品、订单、会员、设置。
3. 先核对列表、详情、图片、配送范围设置读取。变更商品、收款、退款、送达或取货完成仅使用隔离测试数据。
4. 店铺配送顺序为开始配送 → 登记收款 → 确认送达；自提为登记收款 → 取货完成。付款与交货进度独立。
5. 手机确认通过后，确定上传版本号及说明，再通过微信 CLI 上传发布产物。公众平台设体验版、提交审核与正式发布仍由管理员处理。

本机编译与小程序上传不要求重新部署服务器，也不启动本机 Java、MySQL、Redis 或 Docker。

## 首次启动检查

主包首页会在未登录时进入登录分包。首页使用的账号密码弹窗应保留在 `src/components/account-password-form/`，不能同步引用 `pages-core` 分包组件；模拟器直接进入登录页不能代替手机首次启动验收。

发布构建后运行 `check:mp-weixin-entry`，检查启动页及其全局组件是否存在未声明异步占位的分包依赖。手机验收应使用本轮新生成的预览码。

## 会员订单入口

会员列表的“查看订单”直接进入订单列表，携带并固定当前会员的 `userId`，标题为“会员订单”。这里只搜索该会员的订单号，清空搜索仍保留会员筛选。无效会员编号阻止查询，不能回退查询全部订单。普通首页的订单入口继续显示全部订单和原有筛选。
