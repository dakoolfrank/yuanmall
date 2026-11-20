-- 自提门店可先保存为停用草稿；启用前由后端校验联系电话、营业时间和位置完整。
-- 部署前检查目标库，本机仅应用 yuanmall_dev；不修改现有门店数据或状态。
ALTER TABLE trade_delivery_pick_up_store
  MODIFY COLUMN opening_time TIME NULL DEFAULT NULL COMMENT '营业开始时间，待完善门店可为空',
  MODIFY COLUMN closing_time TIME NULL DEFAULT NULL COMMENT '营业结束时间，待完善门店可为空',
  MODIFY COLUMN latitude DOUBLE NULL DEFAULT NULL COMMENT '纬度，待定位时为空',
  MODIFY COLUMN longitude DOUBLE NULL DEFAULT NULL COMMENT '经度，待定位时为空';
