package cn.iocoder.yudao.module.trade.service.delivery;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.config.vo.StoreDeliveryRangeSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryPickUpStoreDO;
import cn.iocoder.yudao.module.trade.dal.mysql.config.TradeConfigMapper;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import cn.iocoder.yudao.module.trade.service.delivery.bo.StoreDeliveryRangeCheckBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

class StoreDeliveryRangeServiceTest extends BaseMockitoUnitTest {
    @InjectMocks private StoreDeliveryRangeService service;
    @Mock private TradeConfigService configService;
    @Mock private TradeConfigMapper configMapper;
    @Mock private DeliveryPickUpStoreService storeService;
    @Mock private MemberAddressApi addressApi;
    @Mock private TradeOrderProperties orderProperties;
    private TradeConfigDO config;
    private DeliveryPickUpStoreDO store;
    private MemberAddressRespDTO address;

    @BeforeEach
    void setup() {
        config = new TradeConfigDO().setId(2L).setStoreDeliveryPickUpStoreId(5L).setStoreDeliveryRadiusMeters(5000);
        store = new DeliveryPickUpStoreDO().setId(5L).setName("配送中心").setStatus(0).setLatitude(0D).setLongitude(0D);
        address = new MemberAddressRespDTO().setId(8L).setUserId(298L).setLatitude(0D).setLongitude(0D);
        lenient().when(configService.getTradeConfig()).thenReturn(config);
        lenient().when(storeService.getDeliveryPickUpStore(5L)).thenReturn(store);
        lenient().when(orderProperties.getStoreDeliveryEnabled()).thenReturn(true);
        lenient().when(addressApi.getAddress(8L, 298L)).thenReturn(address);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1000, 4999.99, 5000})
    void withinInclusiveRadius(double meters) {
        address.setLatitude(Math.toDegrees(meters / 6371008.8));
        StoreDeliveryRangeCheckBO result = service.validate(298L, 8L);
        assertTrue(result.getAvailable());
        assertEquals(0, result.getReasonCode());
        assertEquals((int) Math.ceil(meters), result.getDistanceMeters());
    }

    @ParameterizedTest
    @ValueSource(doubles = {5000.001, 5000.1, 5001, 12000})
    void outsideCannotBeRoundedIntoRange(double meters) {
        address.setLatitude(Math.toDegrees(meters / 6371008.8));
        StoreDeliveryRangeCheckBO result = service.check(298L, 8L);
        assertFalse(result.getAvailable());
        assertEquals(STORE_DELIVERY_OUT_OF_RANGE.getCode(), result.getReasonCode());
        assertTrue(result.getReason().contains("5公里"));
        assertEquals(STORE_DELIVERY_OUT_OF_RANGE.getCode(), assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.validate(298L, 8L)).getCode());
    }

    @Test void missingAddressRequiresLocation() {
        assertEquals(STORE_DELIVERY_LOCATION_REQUIRED.getCode(), service.check(298L, null).getReasonCode());
        verifyNoInteractions(addressApi);
    }
    @Test void foreignOrDeletedAddressIsNotAccepted() {
        assertEquals(STORE_DELIVERY_ADDRESS_NOT_FOUND.getCode(), service.check(299L, 8L).getReasonCode());
        verify(addressApi).getAddress(8L, 299L);
    }
    @Test void missingCoordinateIsNotGuessed() {
        address.setLongitude(null);
        assertServiceException(() -> service.validate(298L, 8L), STORE_DELIVERY_LOCATION_REQUIRED);
    }
    @ParameterizedTest @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, 90.1, -90.1})
    void invalidCoordinatesCannotBypassDistance(double latitude) {
        address.setLatitude(latitude);
        assertEquals(STORE_DELIVERY_LOCATION_REQUIRED.getCode(), service.check(298L, 8L).getReasonCode());
    }
    @Test void configuredRadiusIsUsed() {
        config.setStoreDeliveryRadiusMeters(1000);
        address.setLatitude(Math.toDegrees(1001D / 6371008.8));
        StoreDeliveryRangeCheckBO result = service.check(298L, 8L);
        assertFalse(result.getAvailable());
        assertTrue(result.getReason().contains("1公里"));
    }
    @Test void disabledCenterFailsClosed() {
        store.setStatus(1);
        assertServiceException(() -> service.validate(298L, 8L), STORE_DELIVERY_RANGE_NOT_READY);
        verifyNoInteractions(addressApi);
    }
    @Test void deletedOrOtherTenantCenterFailsClosed() {
        when(storeService.getDeliveryPickUpStore(5L)).thenReturn(null);
        assertServiceException(() -> service.validate(298L, 8L), STORE_DELIVERY_RANGE_NOT_READY);
    }
    @Test void missingConfigFailsClosed() {
        when(configService.getTradeConfig()).thenReturn(null);
        assertServiceException(() -> service.validate(298L, 8L), STORE_DELIVERY_RANGE_NOT_READY);
    }
    @Test void globalSwitchIsRespected() {
        when(orderProperties.getStoreDeliveryEnabled()).thenReturn(false);
        assertFalse(service.getConfig().getEnabled());
    }
    @Test void saveOnlyRangeFields() {
        config.setDeliveryExpressFreeEnabled(true).setDeliveryPickUpEnabled(true);
        when(configMapper.updateById(any(TradeConfigDO.class))).thenReturn(1);
        service.saveConfig(new StoreDeliveryRangeSaveReqVO().setPickUpStoreId(5L).setRadiusMeters(6000));
        ArgumentCaptor<TradeConfigDO> capture = ArgumentCaptor.forClass(TradeConfigDO.class);
        verify(configMapper).updateById(capture.capture());
        assertEquals(2L, capture.getValue().getId());
        assertEquals(6000, capture.getValue().getStoreDeliveryRadiusMeters());
        assertNull(capture.getValue().getDeliveryExpressFreeEnabled());
        assertNull(capture.getValue().getDeliveryPickUpEnabled());
    }
    @Test void invalidCenterCannotBeSaved() {
        store.setLatitude(null);
        assertServiceException(() -> service.saveConfig(new StoreDeliveryRangeSaveReqVO()
                .setPickUpStoreId(5L).setRadiusMeters(5000)), STORE_DELIVERY_RANGE_NOT_READY);
        verifyNoInteractions(configMapper);
    }
}
