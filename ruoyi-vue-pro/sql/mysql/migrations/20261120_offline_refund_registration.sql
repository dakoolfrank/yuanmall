-- 增量迁移：先检查列是否已存在；本机仅应用 yuanmall_dev，不更新生产库或现有订单。
-- 取消与退款分开：登记实际全额线下退款，原付款记录不变，不创建支付渠道退款单。
ALTER TABLE trade_order
  ADD COLUMN offline_refund_method VARCHAR(8) NULL COMMENT '线下退款方式 cash现金 scan微信转回',
  ADD COLUMN offline_refund_amount INT NULL COMMENT '已线下退款金额（分）',
  ADD COLUMN offline_refund_operator_id BIGINT NULL COMMENT '确认线下退款管理员',
  ADD COLUMN offline_refund_time DATETIME NULL COMMENT '确认已线下退款时间';

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, status, visible, keep_alive, always_show, creator, updater)
SELECT '确认线下退款', 'trade:order:confirm-offline-refund', 3, 100, parent_id, '', '#', 0, 1, 1, 1, 'migration', 'migration'
FROM system_menu WHERE permission='trade:order:update' AND deleted=0
AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='trade:order:confirm-offline-refund' AND deleted=0)
LIMIT 1;

-- 新权限只授予现有租户 1 的超级管理员角色，不扩展普通角色权限。
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id)
SELECT r.id,m.id,'migration','migration',r.tenant_id
FROM system_role r JOIN system_menu m ON m.permission='trade:order:confirm-offline-refund' AND m.deleted=0
WHERE r.code='super_admin' AND r.tenant_id=1 AND r.deleted=0
AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=r.tenant_id AND rm.deleted=0);
