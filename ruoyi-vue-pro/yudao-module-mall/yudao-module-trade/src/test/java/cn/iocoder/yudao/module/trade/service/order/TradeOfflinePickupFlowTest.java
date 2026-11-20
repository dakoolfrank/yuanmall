package cn.iocoder.yudao.module.trade.service.order;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderOfflineReceiptReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryPickUpStoreDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.yudao.module.trade.framework.order.core.utils.TradeOrderLogUtils;
import cn.iocoder.yudao.module.trade.service.cart.CartService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryPickUpStoreService;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import cn.iocoder.yudao.module.trade.service.price.TradePriceService;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 自提见面付款：下单、独立收款、已付款后取货，覆盖拒绝与重复操作。 */
@MockitoSettings(strictness = Strictness.LENIENT) // 共用完整订单依赖，拒绝路径会提前退出。
class TradeOfflinePickupFlowTest extends BaseMockitoUnitTest {
    @InjectMocks private TradeOrderUpdateServiceImpl orders;
    @InjectMocks private TradeStoreOrderService receipts;
    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeOrderItemMapper itemMapper;
    @Mock private TradePriceService tradePriceService;
    @Mock private CartService cartService;
    @Mock private MemberAddressApi addressApi;
    @Mock private TradeNoRedisDAO tradeNoRedisDAO;
    @Mock private PayOrderApi payOrderApi;
    @Mock private DeliveryPickUpStoreService pickUpStoreService;
    @Mock private TradeOrderHandler handler;
    private MockedStatic<SpringUtil> spring;
    private MockedStatic<TradeOrderLogUtils> logs;

    @BeforeEach void prepare() {
        ReflectionTestUtils.setField(orders, "tradeOrderHandlers", Collections.singletonList(handler));
        ReflectionTestUtils.setField(receipts, "handlers", Collections.singletonList(handler));
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getBean(TradeOrderUpdateServiceImpl.class)).thenReturn(orders);
        logs = mockStatic(TradeOrderLogUtils.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(cartService.getCartList(anyLong(), anySet())).thenReturn(Collections.emptyList());
        when(tradeNoRedisDAO.generate(anyString())).thenReturn("pickup-unit-order");
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(price());
        doAnswer(invocation -> {
            invocation.getArgument(0, TradeOrderDO.class).setId(9L);
            return 1;
        }).when(orderMapper).insert(any(TradeOrderDO.class));
        when(orderMapper.updateById(any(TradeOrderDO.class))).thenReturn(1);
        when(itemMapper.selectListByOrderId(9L)).thenReturn(Collections.emptyList());
        when(orderMapper.updateByIdAndStatus(eq(9L), eq(10), any())).thenReturn(1);
        when(pickUpStoreService.getDeliveryPickUpStore(5L)).thenReturn(new DeliveryPickUpStoreDO()
                .setId(5L).setVerifyUserIds(Collections.singletonList(7L)));
    }

    @AfterEach void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        logs.close();
        spring.close();
    }

    private AppTradeOrderCreateReqVO request() {
        AppTradeOrderCreateReqVO request = new AppTradeOrderCreateReqVO();
        request.setDeliveryType(2);
        request.setPaymentMethod(2);
        request.setPointStatus(false);
        request.setPickUpStoreId(5L);
        request.setReceiverName("取货测试联系人");
        request.setReceiverMobile("13800000000");
        request.setItems(Collections.singletonList(new AppTradeOrderSettlementReqVO.Item().setSkuId(38L).setCount(1)));
        return request;
    }

    private TradePriceCalculateRespBO price() {
        return new TradePriceCalculateRespBO().setType(0).setTotalPoint(0).setUsePoint(0).setGivePoint(0)
                .setGiveCouponTemplateCounts(Collections.emptyMap()).setPromotions(Collections.emptyList())
                .setCoupons(Collections.emptyList())
                .setPrice(new TradePriceCalculateRespBO.Price().setTotalPrice(1000).setPayPrice(1000)
                        .setDeliveryPrice(0).setDiscountPrice(0).setCouponPrice(0).setPointPrice(0).setVipPrice(0))
                .setItems(Collections.singletonList(new TradePriceCalculateRespBO.OrderItem()
                        .setSpuId(642L).setSkuId(38L).setCount(1).setPrice(1000).setPayPrice(1000)
                        .setSelected(true).setSpuName("取货测试商品")));
    }

    private TradeOrderDO create() {
        TradeOrderDO order = orders.createOrder(10L, request());
        when(orderMapper.selectById(9L)).thenReturn(order);
        when(orderMapper.selectForUpdate(9L)).thenReturn(order);
        return order;
    }

    private TradeOrderOfflineReceiptReqVO receipt(int amount, String method) {
        TradeOrderOfflineReceiptReqVO request = new TradeOrderOfflineReceiptReqVO();
        request.setId(9L);
        request.setAmount(amount);
        request.setMethod(method);
        return request;
    }

    @Test void settlementAcceptsPickupAndKeepsZeroDeliveryFee() {
        assertEquals(1000, orders.settlementOrder(10L, request()).getPrice().getPayPrice());
        verify(tradePriceService).calculateOrderPrice(argThat(request -> request.getDeliveryType() == 2
                && request.getPickUpStoreId() == 5L));
        verifyNoInteractions(orderMapper, payOrderApi);
    }

    @Test void rejectsExpressAndMarketingBeforeCreatingOrder() {
        AppTradeOrderCreateReqVO request = request();
        request.setDeliveryType(1);
        assertServiceException(() -> orders.createOrder(10L, request), OFFLINE_ORDER_INVALID);
        request.setDeliveryType(2);
        request.setSeckillActivityId(1L);
        assertServiceException(() -> orders.settlementOrder(10L, request), OFFLINE_ORDER_INVALID);
        verifyNoInteractions(tradePriceService, orderMapper, payOrderApi);
    }

    @Test void createDoesNotPretendPaidOrCreateWechatPayment() {
        TradeOrderDO order = create();
        assertEquals(10, order.getStatus());
        assertFalse(order.getPayStatus());
        assertNull(order.getPayOrderId());
        assertEquals(2, order.getDeliveryType());
        assertEquals(2, order.getPaymentMethod());
        assertEquals(5L, order.getPickUpStoreId());
        assertNotNull(order.getPickUpVerifyCode());
        assertEquals(1000, order.getPayPrice());
        assertEquals(0, order.getDeliveryPrice());
        verify(handler).afterOrderCreate(eq(order), anyList());
        verifyNoInteractions(payOrderApi);
    }

    @Test void pickupRequiresPaymentThenCompletesOnlyOnce() {
        TradeOrderDO order = create();
        assertServiceException(() -> orders.pickUpOrderByAdmin(7L, 9L), OFFLINE_RECEIPT_INVALID);
        verify(orderMapper, never()).updateByIdAndStatus(eq(9L), anyInt(), any());
        receipts.confirmReceipt(receipt(1000, "cash"), 7L);
        assertEquals(10, order.getStatus());
        assertTrue(order.getPayStatus());
        verify(orderMapper).updateById(argThat((TradeOrderDO row) -> Boolean.TRUE.equals(row.getPayStatus())
                && row.getReceiptAmount() == 1000 && row.getReceiptOperatorId() == 7L
                && "cash".equals(row.getReceiptMethod()) && row.getReceiptTime() != null && row.getStatus() == null));
        orders.pickUpOrderByAdmin(7L, 9L);
        assertEquals(30, order.getStatus());
        assertNotNull(order.getReceiveTime());
        assertServiceException(() -> orders.pickUpOrderByAdmin(7L, 9L), ORDER_PICK_UP_FAIL_STATUS_NOT_UNDELIVERED);
        verify(handler, times(1)).afterPayOrder(eq(order), anyList());
        verify(handler, times(1)).afterReceiveOrder(order);
        verifyNoInteractions(payOrderApi);
    }

    @Test void receiptRejectsMismatchForgedMethodAndDuplicate() {
        TradeOrderDO order = create();
        assertServiceException(() -> receipts.confirmReceipt(receipt(999, "cash"), 7L), OFFLINE_RECEIPT_INVALID);
        assertServiceException(() -> receipts.confirmReceipt(receipt(1000, "wx_lite"), 7L), OFFLINE_RECEIPT_INVALID);
        verify(orderMapper, never()).updateById(any(TradeOrderDO.class));
        receipts.confirmReceipt(receipt(1000, "scan"), 7L);
        assertEquals(10, order.getStatus());
        assertServiceException(() -> receipts.confirmReceipt(receipt(1000, "scan"), 7L), OFFLINE_RECEIPT_CONFLICT);
        verify(handler, times(1)).afterPayOrder(eq(order), anyList());
    }

    @Test void pickupCannotUseStoreDispatchOrCompleteEndpoints() {
        create().setPayStatus(true);
        assertServiceException(() -> receipts.startDelivery(9L, 7L), OFFLINE_ORDER_INVALID);
        assertServiceException(() -> receipts.completeDelivery(9L, 7L), OFFLINE_ORDER_INVALID);
        verify(orderMapper, never()).updateByIdAndStatus(eq(9L), anyInt(), any());
    }

    @Test void pickupRejectsUnboundStaffAndRefundedOfflineOrder() {
        TradeOrderDO order = create().setPayStatus(true);
        assertServiceException(() -> orders.pickUpOrderByAdmin(8L, 9L), ORDER_PICK_UP_FAIL_NOT_VERIFY_USER);
        order.setRefundStatus(1);
        assertServiceException(() -> orders.pickUpOrderByAdmin(7L, 9L), OFFLINE_RECEIPT_INVALID);
        verify(orderMapper, never()).updateByIdAndStatus(eq(9L), anyInt(), any());
    }

    @Test void pickupUsesLockedStateInsteadOfStalePaidSnapshot() {
        TradeOrderDO current = create();
        TradeOrderDO stale = new TradeOrderDO().setId(9L).setPayStatus(true);
        when(orderMapper.selectById(9L)).thenReturn(stale);
        assertServiceException(() -> orders.pickUpOrderByAdmin(7L, 9L), OFFLINE_RECEIPT_INVALID);
        assertFalse(current.getPayStatus());
        verify(orderMapper).selectForUpdate(9L);
        verify(orderMapper, never()).updateByIdAndStatus(eq(9L), anyInt(), any());
    }

    @Test void receiptRejectsPickupWithDispatchStatusOrOnlinePayment() {
        TradeOrderDO order = create().setStatus(20);
        assertServiceException(() -> receipts.confirmReceipt(receipt(1000, "cash"), 7L), OFFLINE_RECEIPT_INVALID);
        order.setStatus(10).setPaymentMethod(1).setPayOrderId(100L);
        assertServiceException(() -> receipts.confirmReceipt(receipt(1000, "cash"), 7L), OFFLINE_RECEIPT_INVALID);
        verify(orderMapper, never()).updateById(any(TradeOrderDO.class));
    }

    @Test void missingTenantScopedRowCannotBePickedUp() {
        create().setPayStatus(true);
        when(orderMapper.selectForUpdate(9L)).thenReturn(null);
        assertServiceException(() -> orders.pickUpOrderByAdmin(7L, 9L), ORDER_NOT_FOUND);
        verify(orderMapper, never()).updateByIdAndStatus(eq(9L), anyInt(), any());
    }

    @Test void verificationCodeUsesTheSamePaidPickupFlow() {
        TradeOrderDO order = create().setPayStatus(true);
        when(orderMapper.selectOneByPickUpVerifyCode(order.getPickUpVerifyCode())).thenReturn(order);
        orders.pickUpOrderByAdmin(7L, order.getPickUpVerifyCode());
        assertEquals(30, order.getStatus());
        verify(orderMapper).selectForUpdate(9L);
    }
}
