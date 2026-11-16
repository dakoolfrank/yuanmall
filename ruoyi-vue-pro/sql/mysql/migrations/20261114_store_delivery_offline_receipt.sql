-- 增量迁移：执行前检查目标库；本机只应用 yuanmall_dev，生产库不要自动执行。
-- 不修改现有订单价格、支付单或运费模板。已存在订单默认仍为在线付款。
ALTER TABLE trade_order
  ADD COLUMN payment_method TINYINT NOT NULL DEFAULT 1 COMMENT '1在线支付 2见面付款',
  ADD COLUMN receipt_method VARCHAR(8) NULL COMMENT 'cash现金 scan现场扫码',
  ADD COLUMN receipt_amount INT NULL COMMENT '线下实收金额（分）',
  ADD COLUMN receipt_operator_id BIGINT NULL COMMENT '确认收款管理员',
  ADD COLUMN receipt_time DATETIME NULL COMMENT '线下确认收款时间';

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, status, visible, keep_alive, always_show, creator, updater)
SELECT '确认见面收款', 'trade:order:confirm-offline-payment', 3, 99, parent_id, '', '#', 0, 1, 1, 1, 'migration', 'migration'
FROM system_menu WHERE permission='trade:order:update' AND deleted=0
AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='trade:order:confirm-offline-payment' AND deleted=0)
LIMIT 1;

INSERT INTO system_dict_data (sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 3,'店铺配送','3','trade_delivery_type',0,'','','免费店铺配送','migration','migration'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='trade_delivery_type' AND value='3' AND deleted=0);
