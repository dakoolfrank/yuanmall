-- 增量迁移：先备份并迁移，再部署新 JAR。禁止重跑初始化脚本。
ALTER TABLE member_address
    ADD COLUMN latitude DOUBLE NULL COMMENT '收货位置纬度 GCJ-02',
    ADD COLUMN longitude DOUBLE NULL COMMENT '收货位置经度 GCJ-02';
ALTER TABLE trade_config
    ADD COLUMN store_delivery_pick_up_store_id BIGINT NULL COMMENT '店铺配送中心自提点',
    ADD COLUMN store_delivery_radius_meters INT NOT NULL DEFAULT 5000 COMMENT '配送直线半径 米';
-- 不猜测历史收货位置。中心由各租户 Admin 设置，不写死业务门店 ID。
