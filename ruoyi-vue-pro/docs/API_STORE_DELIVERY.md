# 园园杂货铺：店铺配送与见面付款接口

2026-11-16 更新。当前服务器开发环境 `https://mall.nexrur.ai`，同一 Java 后端。

## 顾客端约定

结算 `GET /app-api/trade/order/settlement`、创建 `POST /app-api/trade/order/create`：

- `deliveryType`：1 快递（原逻辑）、2 自提（保留）、3 店铺自己送。
- `paymentMethod`：1 在线付款、2 见面付款；不传或 null 默认在线付款。
- 店铺自己送免费，校验收件地址归属；复用支持快递商品的可配送能力，也允许商品显式配置方式 3。纯自提商品不能转为配送。尚无地域限制、起送金额配置，不以此声称已完成配送范围验收。
- 见面付款仅支持普通店铺配送，不支持快递代收和营销活动订单。
- 见面付款创建后 `status=10` 待配送、`payStatus=false`、`payOrderId=null`。不得因 status=10 就当成已付款；依据 `paymentMethod` 和 `payStatus` 显示“待见面收款”，不拉起在线支付。
- 在线支付保持旧逻辑，使用返回的支付单拉起支付。
- 已存在订单默认 paymentMethod=1，不转换原订单或改其金额。

详情与列表增加 paymentMethod；详情提供 receiptMethod、receiptAmount（分）、receiptTime；顾客端不返回收款管理员 ID。待配送且未收款的见面付款订单允许顾客取消；开始配送后不允许走原未付款取消接口。未收款不可确认收货或自动收货。

## 管理端

- `PUT /admin-api/trade/order/store-delivery?id=...`，权限 `trade:order:update`：开始自送，status 10→20；见面付款允许未收款发出，在线支付必须已付款。
- `PUT /admin-api/trade/order/confirm-offline-payment`，独立权限 `trade:order:confirm-offline-payment`，JSON `{ "id": 订单ID, "amount": 应收分值, "method": "cash或scan" }`。
- 只接受店铺配送、见面付款、待配送或配送中、未登记付款、未退款订单；金额必须与 payPrice 精确相等。管理员在线下收到钱后点击“已付款”进行人工登记，记录管理员 ID、时间、实收方式和金额。配送状态保持原来的 10 或 20，payStatus=true，payChannelCode 为 offline_cash/offline_scan，不创建或修改微信支付单。
- `PUT /admin-api/trade/order/store-receive?id=...`，权限 `trade:order:update`：已收款且配送中才能确认送达，status 20→30。
- 操作持有当前租户订单行锁，重复收款拒绝；支付后置处理在收款事务内只执行一次。订单调价入口拒绝见面付款订单，防止与收款并发改变金额。
- 首版见面收款不接在线退款接口，售后申请明确提示联系店主核实线下退款。没有伪造已退款记录，线下退款工作流尚未实现。

## 微信设置

物流与收款设置页面保留，当前首页入口按用户要求隐藏；不展示通用支付应用列表。

微信收款按当前租户应用标识 mall 查找应用与 wx_lite 渠道，不写死应用编号。实际商户配置仍在原支付渠道界面维护。当前开发库商城应用没有微信小程序支付渠道记录，不能声称已经可以实扣款。

微信物流设置：`GET/PUT /admin-api/trade/delivery/wechat/config`，使用运费模板查询/更新权限；仅保存寄件人、联系电话、寄件省市区及详细地址、快递代码 deliveryId、合作账号 bizId、服务类型 serviceType，按租户隔离。`GET /admin-api/trade/delivery/wechat/accounts` 使用已有顾客小程序客户端调用微信物流助手，返回安全字段的绑定账号列表；失败不透传 Token 或请求内容。

微信 AppID/AppSecret 沿用已有小程序配置；商户密钥与证书仍在原支付渠道配置，不进入源码。物流设置保存、账号同步不等于实际生成运单；自动生成电子面单、真实快递下单与取消接口仍需后续实现及真实账号联调，当前订单仍可用原手填快递单号发货。

## 数据迁移与部署

只给当前开发库 yuanmall_dev 执行：

- `sql/mysql/migrations/20261114_store_delivery_offline_receipt.sql`：订单新增付款/收款字段、收款权限、店铺配送字典。ALTER 为一次性迁移，执行前检查列是否存在；不重复运行初始化脚本。
- `sql/mysql/migrations/20261114_wechat_logistics_settings.sql`：新增租户寄件设置表。
- 本机 `application-serverdev.properties` 明确开启 `yudao.trade.order.store-delivery-enabled=true`；未开启时店铺配送请求拒绝，避免其他部署静默开放。

生产库和生产 JAR 未修改。旧模板 4 仍有错误快递收费；旧订单 282、283 在此前历史订单清理中已逻辑删除，其原始金额及支付记录未被改写。店铺自送不使用该模板，但快递配送尚不能按已修复验收；不得在部署时恢复或重跑历史订单清理。
