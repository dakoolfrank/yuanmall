package cn.iocoder.yudao.module.trade.service.delivery;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.config.vo.StoreDeliveryRangeSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryPickUpStoreDO;
import cn.iocoder.yudao.module.trade.dal.mysql.config.TradeConfigMapper;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import cn.iocoder.yudao.module.trade.service.delivery.bo.StoreDeliveryRangeConfigBO;
import cn.iocoder.yudao.module.trade.service.delivery.bo.StoreDeliveryRangeCheckBO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import javax.annotation.Resource;
import javax.validation.Valid;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

@Service
@Validated
public class StoreDeliveryRangeService {
    private static final double EARTH_RADIUS_METERS = 6371008.8;
    @Resource private TradeConfigService tradeConfigService;
    @Resource private TradeConfigMapper tradeConfigMapper;
    @Resource private DeliveryPickUpStoreService deliveryPickUpStoreService;
    @Resource private MemberAddressApi addressApi;
    @Resource private TradeOrderProperties orderProperties;

    public StoreDeliveryRangeConfigBO getConfig() {
        TradeConfigDO config = tradeConfigService.getTradeConfig();
        StoreDeliveryRangeConfigBO result = new StoreDeliveryRangeConfigBO().setEnabled(false);
        if (config == null) return result;
        result.setPickUpStoreId(config.getStoreDeliveryPickUpStoreId())
                .setRadiusMeters(config.getStoreDeliveryRadiusMeters());
        DeliveryPickUpStoreDO store = config.getStoreDeliveryPickUpStoreId() == null ? null
                : deliveryPickUpStoreService.getDeliveryPickUpStore(config.getStoreDeliveryPickUpStoreId());
        if (store != null) result.setStoreName(store.getName())
                .setCenterLatitude(store.getLatitude()).setCenterLongitude(store.getLongitude());
        result.setEnabled(Boolean.TRUE.equals(orderProperties.getStoreDeliveryEnabled())
                && storeReady(store) && result.getRadiusMeters() != null
                && result.getRadiusMeters() > 0 && result.getRadiusMeters() <= 100000);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(@Valid StoreDeliveryRangeSaveReqVO request) {
        if (!storeReady(deliveryPickUpStoreService.getDeliveryPickUpStore(request.getPickUpStoreId()))) {
            throw exception(STORE_DELIVERY_RANGE_NOT_READY);
        }
        TradeConfigDO config = tradeConfigService.getTradeConfig();
        if (config == null) throw exception(STORE_DELIVERY_RANGE_NOT_READY);
        // 非空字段更新策略，只更新这两项，不能回写旧的整份交易设置。
        if (tradeConfigMapper.updateById(new TradeConfigDO().setId(config.getId())
                .setStoreDeliveryPickUpStoreId(request.getPickUpStoreId())
                .setStoreDeliveryRadiusMeters(request.getRadiusMeters())) != 1) {
            throw exception(STORE_DELIVERY_RANGE_NOT_READY);
        }
    }

    /** 只查询当前租户、当前会员的持久化地址，不接受客户端上报的距离或门店中心。 */
    public StoreDeliveryRangeCheckBO check(Long userId, Long addressId) {
        StoreDeliveryRangeConfigBO config = getConfig();
        StoreDeliveryRangeCheckBO result = new StoreDeliveryRangeCheckBO().setAvailable(false)
                .setPickUpStoreId(config.getPickUpStoreId()).setRadiusMeters(config.getRadiusMeters());
        if (!Boolean.TRUE.equals(config.getEnabled())) return result
                .setReasonCode(STORE_DELIVERY_RANGE_NOT_READY.getCode()).setReason(STORE_DELIVERY_RANGE_NOT_READY.getMsg());
        if (addressId == null) return result.setReasonCode(STORE_DELIVERY_LOCATION_REQUIRED.getCode())
                .setReason(STORE_DELIVERY_LOCATION_REQUIRED.getMsg());
        MemberAddressRespDTO address = userId == null ? null : addressApi.getAddress(addressId, userId);
        if (address == null) return result.setReasonCode(STORE_DELIVERY_ADDRESS_NOT_FOUND.getCode())
                .setReason(STORE_DELIVERY_ADDRESS_NOT_FOUND.getMsg());
        if (!validPoint(address.getLatitude(), address.getLongitude())) return result
                .setReasonCode(STORE_DELIVERY_LOCATION_REQUIRED.getCode()).setReason(STORE_DELIVERY_LOCATION_REQUIRED.getMsg());
        double distance = distanceMeters(config.getCenterLatitude(), config.getCenterLongitude(),
                address.getLatitude(), address.getLongitude());
        result.setDistanceMeters((int) Math.ceil(Math.max(0, distance - 0.000001)));
        if (distance > config.getRadiusMeters() + 0.000001) {
            String km = BigDecimal.valueOf(config.getRadiusMeters()).movePointLeft(3).stripTrailingZeros().toPlainString();
            return result.setReasonCode(STORE_DELIVERY_OUT_OF_RANGE.getCode())
                    .setReason("该地址超出" + km + "公里配送范围，请选择自提");
        }
        return result.setAvailable(true).setReasonCode(0).setReason("可配送");
    }

    public StoreDeliveryRangeCheckBO validate(Long userId, Long addressId) {
        StoreDeliveryRangeCheckBO result = check(userId, addressId);
        if (!Boolean.TRUE.equals(result.getAvailable())) throw new ServiceException(result.getReasonCode(), result.getReason());
        return result;
    }

    private static boolean storeReady(DeliveryPickUpStoreDO store) {
        return store != null && CommonStatusEnum.ENABLE.getStatus().equals(store.getStatus())
                && validPoint(store.getLatitude(), store.getLongitude());
    }

    private static boolean validPoint(Double latitude, Double longitude) {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(Math.min(1, a)), Math.sqrt(Math.max(0, 1 - a)));
    }
}
