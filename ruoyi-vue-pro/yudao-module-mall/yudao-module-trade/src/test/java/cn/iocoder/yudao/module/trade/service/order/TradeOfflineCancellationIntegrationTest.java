package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.point.MemberPointApi;
import cn.iocoder.yudao.module.member.enums.MemberExperienceBizTypeEnum;
import cn.iocoder.yudao.module.member.enums.point.MemberPointBizTypeEnum;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.api.refund.PayRefundApi;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.product.api.comment.ProductCommentApi;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuUpdateStockReqDTO;
import cn.iocoder.yudao.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.yudao.module.promotion.api.coupon.CouponApi;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderOfflineReceiptReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderOfflineRefundReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryPickUpStoreDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderLogDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderLogMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.framework.order.core.aop.TradeOrderLogAspect;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.yudao.module.trade.service.cart.CartService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryPickUpStoreService;
import cn.iocoder.yudao.module.trade.service.message.TradeMessageService;
import cn.iocoder.yudao.module.trade.service.order.handler.*;
import cn.iocoder.yudao.module.trade.service.price.TradePriceService;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real H2 SQL, tenant interception, transactions, row locks, cancellation handlers and audit aspect. */
@Import({TradeOrderUpdateServiceImpl.class, TradeStoreOrderService.class, TradeOrderLogServiceImpl.class,
        TradeOrderLogAspect.class, TradeProductSkuOrderHandler.class, TradeCouponOrderHandler.class,
        TradeMemberPointOrderHandler.class, TradeOfflineCancellationIntegrationTest.BlockingHandler.class,
        TradeOfflineCancellationIntegrationTest.Config.class})
@MockBean({CartService.class, TradePriceService.class, TradeNoRedisDAO.class, DeliveryExpressService.class,
        TradeMessageService.class, MemberAddressApi.class, ProductCommentApi.class, SocialClientApi.class,
        CombinationRecordApi.class, TradeOrderQueryService.class, AfterSaleService.class})
class TradeOfflineCancellationIntegrationTest extends BaseDbUnitTest {
    private static final long ORDER = 9001L, USER = 10L, ADMIN = 7L, SKU = 7001L;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 19, 10, 0);

    @Resource private TradeOrderUpdateServiceImpl orders;
    @Resource private TradeStoreOrderService stores;
    @Resource private TradeOrderMapper orderMapper;
    @Resource private TradeOrderItemMapper itemMapper;
    @Resource private TradeOrderLogMapper logMapper;
    @Resource private DataSource dataSource;
    @SpyBean private TradeOrderLogServiceImpl orderLogService;
    private JdbcTemplate jdbc;
    @Resource private TradeProductSkuOrderHandler skuHandler;
    @Resource private TradeCouponOrderHandler couponHandler;
    @Resource private TradeMemberPointOrderHandler pointHandler;
    @Resource private BlockingHandler blocking;
    @MockBean private ProductSkuApi skuApi;
    @MockBean private MemberPointApi pointApi;
    @MockBean private MemberLevelApi levelApi;
    @MockBean private CouponApi couponApi;
    @MockBean private PayOrderApi payApi;
    @MockBean private PayRefundApi refundApi;
    @MockBean private DeliveryPickUpStoreService pickupStores;
    @MockBean private TradeOrderProperties properties;

    @TestConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean @Lazy(false)
        SmartInitializingSingleton tenantSqlForIntegrationTests(MybatisPlusInterceptor plugins) {
            return () -> {
                List<InnerInterceptor> interceptors = new ArrayList<>(plugins.getInterceptors());
                interceptors.add(0, new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
                plugins.setInterceptors(interceptors);
            };
        }
    }

    public static class BlockingHandler implements TradeOrderHandler {
        volatile String operation;
        volatile CountDownLatch entered, release;
        void arm(String operation) {
            this.operation = operation;
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }
        void reset() { operation = null; }
        void pause(String operation) {
            if (!operation.equals(this.operation)) return;
            entered.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS), "Test lock was not released"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }
        @Override public void afterCancelOrder(TradeOrderDO order, List<TradeOrderItemDO> items) { pause("cancel"); }
        @Override public void afterReceiveOrder(TradeOrderDO order) { pause("complete"); }
        @Override public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> items) { pause("receipt"); }
        @Override public void afterDeliveryOrder(TradeOrderDO order) { pause("dispatch"); }
    }

    @BeforeEach
    void prepare() throws Exception {
        context(USER, 1);
        try (java.sql.Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
        }
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS cancellation_test_stock (tenant_id bigint, sku_id bigint, stock int, PRIMARY KEY(tenant_id,sku_id))");
        jdbc.update("DELETE FROM cancellation_test_stock");
        jdbc.update("INSERT INTO cancellation_test_stock VALUES (1,?,3)", SKU);
        List<TradeOrderHandler> handlers = Arrays.asList(skuHandler, couponHandler, pointHandler, blocking);
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(orders), "tradeOrderHandlers", handlers);
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(stores), "handlers", handlers);
        blocking.reset();
        doAnswer(invocation -> {
            ProductSkuUpdateStockReqDTO request = invocation.getArgument(0);
            for (ProductSkuUpdateStockReqDTO.Item item : request.getItems()) {
                assertEquals(1, jdbc.update("UPDATE cancellation_test_stock SET stock=stock+? WHERE tenant_id=? AND sku_id=?",
                        item.getIncrCount(), TenantContextHolder.getRequiredTenantId(), item.getId()));
            }
            return null;
        }).when(skuApi).updateSkuStock(any());
        when(pickupStores.getDeliveryPickUpStore(5L)).thenReturn(new DeliveryPickUpStoreDO()
                .setId(5L).setVerifyUserIds(Collections.singletonList(ADMIN)));
    }

    @AfterEach
    void cleanup() {
        blocking.reset();
        if (blocking.release != null) blocking.release.countDown();
        RequestContextHolder.resetRequestAttributes();
        TenantContextHolder.clear();
    }

    private static void context(long user, int type) {
        TenantContextHolder.setTenantId(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        WebFrameworkUtils.setLoginUserId(request, user);
        WebFrameworkUtils.setLoginUserType(request, type);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private TradeOrderDO seed(int delivery, int status, boolean paid) {
        TradeOrderDO order = TradeOrderDO.builder().id(ORDER).no("isolated-cancellation-test").type(0)
                .terminal(30).userId(USER).userIp("127.0.0.1").status(status).productCount(2).commentStatus(false)
                .paymentMethod(2).payStatus(paid).payTime(paid ? PAID_AT : null)
                .payChannelCode(paid ? "offline_scan" : null).receiptMethod(paid ? "scan" : null)
                .receiptAmount(paid ? 1500 : null).receiptTime(paid ? PAID_AT : null)
                .receiptOperatorId(paid ? ADMIN : null).totalPrice(1500).discountPrice(0).deliveryPrice(0)
                .adjustPrice(0).payPrice(1500).deliveryType(delivery).pickUpStoreId(delivery == 2 ? 5L : null)
                .pickUpVerifyCode("87350001").deliveryTime(LocalDateTime.now().minusDays(30))
                .receiverName("isolated fixture").receiverMobile("13900000000").receiverAreaId(110113)
                .receiverDetailAddress("isolated fixture").couponId(22L).couponPrice(0).pointPrice(0)
                .usePoint(2).givePoint(7).refundPrice(0).refundPoint(0).refundStatus(0)
                .giveCouponIds(paid ? Collections.singletonList(33L) : Collections.emptyList())
                .giveCouponTemplateCounts(Collections.emptyMap()).build();
        orderMapper.insert(order);
        itemMapper.insert(new TradeOrderItemDO().setId(9002L).setOrderId(ORDER).setUserId(USER)
                .setSpuId(7000L).setSpuName("isolated fixture").setSkuId(SKU).setCount(2)
                .setPrice(750).setPayPrice(1500).setDiscountPrice(0).setAfterSaleStatus(0)
                .setUsePoint(2).setGivePoint(7));
        return order;
    }

    private int stock() { return jdbc.queryForObject("SELECT stock FROM cancellation_test_stock WHERE tenant_id=1 AND sku_id=?", Integer.class, SKU); }
    private void assertNoCancellation() {
        assertEquals(3, stock());
        assertEquals(0, logMapper.selectListByOrderId(ORDER).size());
        verifyNoInteractions(skuApi, couponApi, pointApi, levelApi, refundApi);
    }

    @ParameterizedTest
    @CsvSource({"2,10,false", "2,10,true", "2,20,false", "2,20,true",
            "3,10,false", "3,10,true", "3,20,false", "3,20,true"})
    void unfinishedOfflineOrdersCanCancelWithoutChangingPayment(int delivery, int status, boolean paid) {
        TradeOrderDO original = seed(delivery, status, paid);
        orders.cancelOrderByMember(USER, ORDER);
        TradeOrderDO current = orderMapper.selectById(ORDER);
        assertEquals(40, current.getStatus());
        assertNotNull(current.getCancelTime());
        assertEquals(original.getPayStatus(), current.getPayStatus());
        assertEquals(original.getPayTime(), current.getPayTime());
        assertEquals(original.getPayChannelCode(), current.getPayChannelCode());
        assertEquals(original.getReceiptTime(), current.getReceiptTime());
        assertEquals(original.getReceiptMethod(), current.getReceiptMethod());
        assertEquals(original.getReceiptAmount(), current.getReceiptAmount());
        assertEquals(original.getReceiptOperatorId(), current.getReceiptOperatorId());
        assertEquals(1500, current.getPayPrice());
        assertEquals(0, current.getRefundPrice());
        assertEquals(0, current.getRefundStatus());
        assertEquals(5, stock());
        List<TradeOrderLogDO> logs = logMapper.selectListByOrderId(ORDER);
        assertEquals(1, logs.size());
        assertEquals(status, logs.get(0).getBeforeStatus());
        assertEquals(40, logs.get(0).getAfterStatus());
        assertEquals(USER, logs.get(0).getUserId());
        verify(pointApi).addPoint(USER, 2, MemberPointBizTypeEnum.ORDER_USE_CANCEL.getType(), String.valueOf(ORDER));
        if (paid) {
            verify(pointApi).reducePoint(USER, 7, MemberPointBizTypeEnum.ORDER_GIVE_CANCEL.getType(), String.valueOf(ORDER));
            verify(levelApi).addExperience(USER, 1500, MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL.getType(), String.valueOf(ORDER));
        } else {
            verifyNoInteractions(levelApi);
        }
        verifyNoInteractions(payApi, refundApi);
    }

    @Test void duplicateCancellationHasOneStockReturnAndOneAudit() {
        seed(3, 20, true);
        orders.cancelOrderByMember(USER, ORDER);
        assertServiceException(() -> orders.cancelOrderByMember(USER, ORDER), ORDER_CANCEL_FAIL_STATUS_NOT_UNPAID);
        assertEquals(5, stock());assertEquals(1, logMapper.selectListByOrderId(ORDER).size());
        verify(skuApi, times(1)).updateSkuStock(any());
        verify(couponApi, times(1)).returnUsedCoupon(22L);
        verify(couponApi, times(1)).invalidateCouponsByAdmin(Collections.singletonList(33L), USER);
    }

    @ParameterizedTest @ValueSource(ints = {30, 40, 50})
    void finishedAndUnknownStatusesCannotCancel(int status) {
        seed(3, status, true);
        assertServiceException(() -> orders.cancelOrderByMember(USER, ORDER), ORDER_CANCEL_FAIL_STATUS_NOT_UNPAID);
        assertNoCancellation();
    }

    @Test void anotherBuyerCannotCancel() {
        seed(3, 20, true);
        assertServiceException(() -> orders.cancelOrderByMember(11L, ORDER), ORDER_NOT_FOUND);
        assertNoCancellation();
    }

    @Test void anotherTenantCannotCancel() {
        seed(3, 20, true);
        TenantContextHolder.setTenantId(2L);
        try { assertServiceException(() -> orders.cancelOrderByMember(USER, ORDER), ORDER_NOT_FOUND); }
        finally { TenantContextHolder.setTenantId(1L); }
        assertEquals(20, orderMapper.selectById(ORDER).getStatus());assertNoCancellation();
    }

    @Test void deletedOrderCannotCancel() {
        seed(3, 20, true);orderMapper.deleteById(ORDER);
        assertServiceException(() -> orders.cancelOrderByMember(USER, ORDER), ORDER_NOT_FOUND);assertNoCancellation();
    }

    @ParameterizedTest @CsvSource({"1,0,0", "3,1,0", "3,0,1"})
    void rejectsUnsupportedOfflineDeliveryMarketingAndRefund(int delivery, int type, int refund) {
        seed(delivery, 10, true);
        orderMapper.updateById(new TradeOrderDO().setId(ORDER).setType(type).setRefundStatus(refund));
        assertServiceException(() -> orders.cancelOrderByMember(USER, ORDER), OFFLINE_ORDER_INVALID);assertNoCancellation();
    }

    @Test void rollbackRestoresStockAndStateWhenPostProcessingFails() {
        seed(3, 20, true);
        doThrow(new IllegalStateException("isolated failure")).when(pointApi).reducePoint(anyLong(), anyInt(), anyInt(), anyString());
        assertThrows(IllegalStateException.class, () -> orders.cancelOrderByMember(USER, ORDER));
        assertEquals(20, orderMapper.selectById(ORDER).getStatus());assertTrue(orderMapper.selectById(ORDER).getPayStatus());
        assertEquals(3, stock());assertEquals(0, logMapper.selectListByOrderId(ORDER).size());
        verify(skuApi).updateSkuStock(any());verifyNoInteractions(payApi, refundApi);
    }

    @Test void legacyOnlineUnpaidStillCancels() {
        seed(1, 0, false);
        orderMapper.updateById(new TradeOrderDO().setId(ORDER).setPaymentMethod(1).setPayOrderId(77L));
        orders.cancelOrderByMember(USER, ORDER);
        assertEquals(40, orderMapper.selectById(ORDER).getStatus());assertEquals(5, stock());verify(payApi).getOrder(77L);
    }

    @ParameterizedTest @ValueSource(ints = {0, 10, 20})
    void onlinePaidNeverSilentlyCancels(int status) {
        seed(1, status, true);orderMapper.updateById(new TradeOrderDO().setId(ORDER).setPaymentMethod(1));
        assertServiceException(() -> orders.cancelOrderByMember(USER, ORDER), ORDER_CANCEL_FAIL_STATUS_NOT_UNPAID);
        assertNoCancellation();verifyNoInteractions(payApi);
    }

    @Test void delayedOnlinePaymentBlocksCancellation() {
        seed(1, 0, false);orderMapper.updateById(new TradeOrderDO().setId(ORDER).setPaymentMethod(1).setPayOrderId(77L));
        when(payApi.getOrder(77L)).thenReturn(new PayOrderRespDTO().setStatus(PayOrderStatusEnum.SUCCESS.getStatus()));
        assertServiceException(() -> orders.cancelOrderByMember(USER, ORDER), ORDER_CANCEL_FAIL_STATUS_NOT_UNPAID);assertNoCancellation();
    }

    @Test void offlineLegacyPayOrderReferenceIsPreservedWithoutGatewayCalls() {
        seed(3, 20, true);orderMapper.updateById(new TradeOrderDO().setId(ORDER).setPayOrderId(77L));
        orders.cancelOrderByMember(USER, ORDER);
        assertEquals(77L, orderMapper.selectById(ORDER).getPayOrderId());verifyNoInteractions(payApi, refundApi);
    }

    @Test void customerAndTimerCannotFinishOfflineOrders() {
        TradeOrderDO order = seed(3, 20, true);
        assertServiceException(() -> orders.receiveOrderByMember(USER, ORDER), OFFLINE_COMPLETE_ADMIN_REQUIRED);
        assertServiceException(() -> orders.receiveOrderBySystem(order), OFFLINE_COMPLETE_ADMIN_REQUIRED);
        when(properties.getReceiveExpireTime()).thenReturn(Duration.ofDays(7));
        assertEquals(0, orders.receiveOrderBySystem());
        assertEquals(20, orderMapper.selectById(ORDER).getStatus());assertNoCancellation();
    }

    @Test void onlineReceiptCannotCompleteAnUnpaidOrder() {
        seed(1, 20, false);
        orderMapper.updateById(new TradeOrderDO().setId(ORDER).setPaymentMethod(1));
        assertServiceException(() -> orders.receiveOrderByMember(USER, ORDER), ORDER_RECEIVE_FAIL_NOT_PAID);
        assertEquals(20, orderMapper.selectById(ORDER).getStatus());assertNoCancellation();
    }

    @Test void legacyPaidOnlineReceiptStillCompletes() {
        seed(1, 20, true);
        orderMapper.updateById(new TradeOrderDO().setId(ORDER).setPaymentMethod(1).setPayChannelCode("wx_lite"));
        orders.receiveOrderByMember(USER, ORDER);
        assertEquals(30, orderMapper.selectById(ORDER).getStatus());assertTrue(orderMapper.selectById(ORDER).getPayStatus());
        assertEquals(3, stock());assertEquals(1, logMapper.selectListByOrderId(ORDER).size());
        verifyNoInteractions(payApi, refundApi);
    }

    @ParameterizedTest @ValueSource(ints = {2, 3})
    void adminCannotFinishUnpaidOfflineOrder(int delivery) {
        seed(delivery, delivery == 2 ? 10 : 20, false);
        assertServiceException(() -> complete(delivery), OFFLINE_RECEIPT_INVALID);assertNoCancellation();
    }

    @ParameterizedTest @CsvSource({"2,true", "2,false", "3,true", "3,false"})
    void cancellationAndCompletionHaveOnlyOneTerminalWinner(int delivery, boolean cancellationFirst) throws Exception {
        seed(delivery, delivery == 2 ? 10 : 20, true);
        blocking.arm(cancellationFirst ? "cancel" : "complete");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> run(cancellationFirst ? () -> orders.cancelOrderByMember(USER, ORDER) : () -> complete(delivery)));
            assertTrue(blocking.entered.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = executor.submit(() -> run(cancellationFirst ? () -> complete(delivery) : () -> orders.cancelOrderByMember(USER, ORDER)));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            blocking.release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));assertFalse(second.get(5, TimeUnit.SECONDS));
            assertEquals(cancellationFirst ? 40 : 30, orderMapper.selectById(ORDER).getStatus());
            assertTrue(orderMapper.selectById(ORDER).getPayStatus());assertEquals(PAID_AT, orderMapper.selectById(ORDER).getReceiptTime());
            assertEquals(cancellationFirst ? 5 : 3, stock());
            assertEquals(1, logMapper.selectListByOrderId(ORDER).size());
            verifyNoInteractions(payApi, refundApi);
        } finally { blocking.release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void concurrentDuplicateCancellationReturnsStockOnce() throws Exception {
        seed(3, 20, true);blocking.arm("cancel");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> run(() -> orders.cancelOrderByMember(USER, ORDER)));
            assertTrue(blocking.entered.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = executor.submit(() -> run(() -> orders.cancelOrderByMember(USER, ORDER)));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            blocking.release.countDown();assertTrue(first.get(5, TimeUnit.SECONDS));assertFalse(second.get(5, TimeUnit.SECONDS));
            assertEquals(5, stock());assertEquals(1, logMapper.selectListByOrderId(ORDER).size());
            verify(skuApi, times(1)).updateSkuStock(any());
        } finally { blocking.release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void cancellationAndReceiptPreserveWhoActuallyCollected(boolean receiptFirst) throws Exception {
        seed(3, 20, false);blocking.arm(receiptFirst ? "receipt" : "cancel");
        TradeOrderOfflineReceiptReqVO receipt = new TradeOrderOfflineReceiptReqVO();receipt.setId(ORDER);receipt.setAmount(1500);receipt.setMethod("cash");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> run(receiptFirst ? () -> stores.confirmReceipt(receipt, ADMIN) : () -> orders.cancelOrderByMember(USER, ORDER)));
            assertTrue(blocking.entered.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = executor.submit(() -> run(receiptFirst ? () -> orders.cancelOrderByMember(USER, ORDER) : () -> stores.confirmReceipt(receipt, ADMIN)));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            blocking.release.countDown();assertTrue(first.get(5, TimeUnit.SECONDS));assertEquals(receiptFirst, second.get(5, TimeUnit.SECONDS));
            TradeOrderDO current = orderMapper.selectById(ORDER);assertEquals(40, current.getStatus());
            assertEquals(receiptFirst, current.getPayStatus());
            if (receiptFirst) { assertEquals(1500, current.getReceiptAmount());assertNotNull(current.getReceiptTime()); }
            else { assertNull(current.getReceiptTime()); }
            assertEquals(5, stock());assertEquals(receiptFirst ? 2 : 1, logMapper.selectListByOrderId(ORDER).size());
            verify(skuApi, times(1)).updateSkuStock(any());verifyNoInteractions(payApi, refundApi);
        } finally { blocking.release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    private TradeOrderOfflineRefundReqVO refundRequest(Integer amount, String method) {
        TradeOrderOfflineRefundReqVO request = new TradeOrderOfflineRefundReqVO();
        request.setId(ORDER);request.setAmount(amount);request.setMethod(method);return request;
    }

    @ParameterizedTest @CsvSource({"2,cash", "2,scan", "3,cash", "3,scan"})
    void canceledPaidOrderRegistersRealRefundWithoutChangingPaymentOrStock(int delivery, String method) {
        TradeOrderDO original = seed(delivery, 40, true);
        context(ADMIN, 2);
        stores.confirmRefund(refundRequest(1500, method), ADMIN);
        TradeOrderDO current = orderMapper.selectById(ORDER);
        assertEquals(40, current.getStatus());assertTrue(current.getPayStatus());
        assertEquals(PAID_AT, current.getPayTime());assertEquals(original.getPayChannelCode(), current.getPayChannelCode());
        assertEquals(original.getReceiptTime(), current.getReceiptTime());
        assertEquals(original.getReceiptMethod(), current.getReceiptMethod());assertEquals(1500, current.getReceiptAmount());
        assertEquals(1500, current.getPayPrice());assertEquals(20, current.getRefundStatus());assertEquals(1500, current.getRefundPrice());
        assertEquals(method, current.getOfflineRefundMethod());assertEquals(1500, current.getOfflineRefundAmount());
        assertEquals(ADMIN, current.getOfflineRefundOperatorId());assertNotNull(current.getOfflineRefundTime());
        assertEquals(3, stock());
        List<TradeOrderLogDO> logs = logMapper.selectListByOrderId(ORDER);assertEquals(1, logs.size());
        assertEquals(13, logs.get(0).getOperateType());assertEquals(40, logs.get(0).getBeforeStatus());assertEquals(40, logs.get(0).getAfterStatus());
        assertEquals(ADMIN, logs.get(0).getUserId());assertEquals(2, logs.get(0).getUserType());
        assertTrue(logs.get(0).getContent().contains("1500"));
        verifyNoInteractions(skuApi, couponApi, pointApi, levelApi, payApi, refundApi);
    }

    @ParameterizedTest @ValueSource(ints = {0, 10, 20, 30, 50})
    void refundRequiresCanceledOrder(int status) {
        seed(3, status, true);
        assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN), OFFLINE_REFUND_INVALID);
        assertNoCancellation();verifyNoInteractions(payApi);
    }

    @ParameterizedTest @CsvSource({"3,2,0,false", "3,1,0,true", "1,2,0,true", "3,2,1,true"})
    void refundRejectsUnpaidOnlineExpressAndMarketing(int delivery, int method, int type, boolean paid) {
        seed(delivery, 40, paid);orderMapper.updateById(new TradeOrderDO().setId(ORDER).setPaymentMethod(method).setType(type));
        assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN), OFFLINE_REFUND_INVALID);
        assertNoCancellation();verifyNoInteractions(payApi);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1, 1499, 1501})
    void refundRejectsWrongAmount(int amount) {
        seed(3, 40, true);
        assertServiceException(() -> stores.confirmRefund(refundRequest(amount, "cash"), ADMIN), OFFLINE_REFUND_INVALID);
        assertNoCancellation();
    }

    @Test void refundRejectsMissingAmountAndUnsupportedMethod() {
        seed(3, 40, true);
        assertServiceException(() -> stores.confirmRefund(refundRequest(null, "cash"), ADMIN), OFFLINE_REFUND_INVALID);
        assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "wx_lite"), ADMIN), OFFLINE_REFUND_INVALID);
        assertNoCancellation();
    }

    @Test void refundRejectsAnotherTenantAndDeletedOrder() {
        seed(3, 40, true);TenantContextHolder.setTenantId(2L);
        try { assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN), ORDER_NOT_FOUND); }
        finally { TenantContextHolder.setTenantId(1L); }
        orderMapper.deleteById(ORDER);
        assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN), ORDER_NOT_FOUND);
        assertNoCancellation();
    }

    @Test void refundRejectsOnlineReferenceAndExistingRefunds() {
        seed(3, 40, true);orderMapper.updateById(new TradeOrderDO().setId(ORDER).setPayOrderId(77L));
        assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN), OFFLINE_REFUND_INVALID);
        jdbc.update("UPDATE trade_order SET pay_order_id=NULL,refund_price=100 WHERE id=?", ORDER);
        assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN), OFFLINE_REFUND_INVALID);
        jdbc.update("UPDATE trade_order SET refund_price=0,refund_status=10 WHERE id=?", ORDER);
        assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN), OFFLINE_REFUND_INVALID);
        assertNoCancellation();verifyNoInteractions(payApi);
    }

    @Test void duplicateRefundLeavesOriginalReceiptAndOneAudit() {
        seed(3, 40, true);stores.confirmRefund(refundRequest(1500, "cash"), ADMIN);
        TradeOrderDO first = orderMapper.selectById(ORDER);
        assertServiceException(() -> stores.confirmRefund(refundRequest(1500, "scan"), ADMIN + 1), OFFLINE_REFUND_CONFLICT);
        TradeOrderDO current = orderMapper.selectById(ORDER);
        assertEquals(first.getOfflineRefundTime(), current.getOfflineRefundTime());assertEquals("cash", current.getOfflineRefundMethod());
        assertEquals(ADMIN, current.getOfflineRefundOperatorId());assertEquals(1, logMapper.selectListByOrderId(ORDER).size());
        assertEquals(3, stock());verifyNoInteractions(skuApi, payApi, refundApi);
    }

    @Test void concurrentRefundsHaveOneRegistrationAndOneAudit() throws Exception {
        seed(3, 40, true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = connection.prepareStatement("SELECT id FROM trade_order WHERE id=? FOR UPDATE")) {
                statement.setLong(1, ORDER);try (java.sql.ResultSet ignored = statement.executeQuery()) { assertTrue(ignored.next()); }
            }
            Future<Boolean> first = executor.submit(() -> run(() -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN)));
            Future<Boolean> second = executor.submit(() -> run(() -> stores.confirmRefund(refundRequest(1500, "scan"), ADMIN + 1)));
            assertThrows(TimeoutException.class, () -> first.get(100, TimeUnit.MILLISECONDS));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            connection.commit();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1500, orderMapper.selectById(ORDER).getOfflineRefundAmount());
            assertEquals(1, logMapper.selectListByOrderId(ORDER).size());assertEquals(3, stock());
            verifyNoInteractions(skuApi, couponApi, pointApi, levelApi, payApi, refundApi);
        } finally { executor.shutdownNow();assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void cancellationThenRefundDoesNotReturnStockTwice() {
        seed(3, 20, true);orders.cancelOrderByMember(USER, ORDER);
        stores.confirmRefund(refundRequest(1500, "scan"), ADMIN);
        assertEquals(5, stock());assertEquals(2, logMapper.selectListByOrderId(ORDER).size());
        assertEquals(40, orderMapper.selectById(ORDER).getStatus());assertTrue(orderMapper.selectById(ORDER).getPayStatus());
        assertEquals(PAID_AT, orderMapper.selectById(ORDER).getReceiptTime());
        verify(skuApi, times(1)).updateSkuStock(any());verifyNoInteractions(payApi, refundApi);
    }

    @Test void refundAuditFailureRollsBackRegistration() {
        seed(3, 40, true);
        doThrow(new IllegalStateException("isolated audit failure")).when(orderLogService).createOrderLog(any());
        assertThrows(IllegalStateException.class, () -> stores.confirmRefund(refundRequest(1500, "cash"), ADMIN));
        TradeOrderDO order = orderMapper.selectById(ORDER);
        assertNull(order.getOfflineRefundTime());assertNull(order.getOfflineRefundAmount());
        assertEquals(0, order.getRefundPrice());assertEquals(0, order.getRefundStatus());
        assertEquals(40, order.getStatus());assertTrue(order.getPayStatus());assertEquals(PAID_AT, order.getReceiptTime());
        assertEquals(0, logMapper.selectListByOrderId(ORDER).size());assertEquals(3, stock());
    }

    @Test void refundResponseMappingKeepsAdminIdentityPrivateFromCustomer() throws Exception {
        seed(3, 40, true);stores.confirmRefund(refundRequest(1500, "cash"), ADMIN);
        TradeOrderDO order = orderMapper.selectById(ORDER);
        cn.iocoder.yudao.module.trade.convert.order.TradeOrderConvert mapper = cn.iocoder.yudao.module.trade.convert.order.TradeOrderConvert.INSTANCE;
        cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDetailRespVO admin = mapper.convert2(order, Collections.emptyList());
        cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderDetailRespVO detail = mapper.convert3(order, Collections.emptyList());
        cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderPageItemRespVO page = mapper.convert02(order, Collections.emptyList());
        assertEquals(ADMIN, admin.getOfflineRefundOperatorId());assertEquals(1500, detail.getOfflineRefundAmount());
        assertEquals(order.getOfflineRefundTime(), detail.getOfflineRefundTime());assertEquals("cash", page.getOfflineRefundMethod());
        assertEquals(1500, page.getRefundPrice());assertEquals(20, page.getRefundStatus());assertTrue(page.getPayStatus());
        assertThrows(NoSuchMethodException.class, () -> detail.getClass().getMethod("getOfflineRefundOperatorId"));
        assertThrows(NoSuchMethodException.class, () -> page.getClass().getMethod("getOfflineRefundOperatorId"));
    }

    @ParameterizedTest @ValueSource(strings = {"cash", "scan"})
    void storeDeliveryMustDispatchThenCollectThenComplete(String method) {
        seed(3, 10, false);
        TradeOrderOfflineReceiptReqVO receipt = new TradeOrderOfflineReceiptReqVO();
        receipt.setId(ORDER);receipt.setAmount(1500);receipt.setMethod(method);
        assertServiceException(() -> stores.confirmReceipt(receipt, ADMIN), OFFLINE_RECEIPT_INVALID);
        assertServiceException(() -> stores.completeDelivery(ORDER, ADMIN), OFFLINE_RECEIPT_INVALID);
        assertFalse(orderMapper.selectById(ORDER).getPayStatus());assertEquals(0, logMapper.selectListByOrderId(ORDER).size());
        stores.startDelivery(ORDER, ADMIN);
        TradeOrderDO dispatched = orderMapper.selectById(ORDER);
        assertEquals(20, dispatched.getStatus());assertFalse(dispatched.getPayStatus());assertNotNull(dispatched.getDeliveryTime());
        assertServiceException(() -> stores.completeDelivery(ORDER, ADMIN), OFFLINE_RECEIPT_INVALID);
        stores.confirmReceipt(receipt, ADMIN);
        TradeOrderDO paid = orderMapper.selectById(ORDER);
        assertEquals(20, paid.getStatus());assertTrue(paid.getPayStatus());assertEquals(1500, paid.getReceiptAmount());
        assertEquals(dispatched.getDeliveryTime(), paid.getDeliveryTime());
        assertServiceException(() -> stores.confirmReceipt(receipt, ADMIN), OFFLINE_RECEIPT_CONFLICT);
        stores.completeDelivery(ORDER, ADMIN);
        TradeOrderDO completed = orderMapper.selectById(ORDER);
        assertEquals(30, completed.getStatus());assertTrue(completed.getPayStatus());assertNotNull(completed.getReceiveTime());
        assertEquals(paid.getReceiptTime(), completed.getReceiptTime());
        assertServiceException(() -> stores.completeDelivery(ORDER, ADMIN), OFFLINE_RECEIPT_INVALID);
        List<TradeOrderLogDO> logs = logMapper.selectListByOrderId(ORDER);assertEquals(3, logs.size());
        logs.sort(java.util.Comparator.comparing(TradeOrderLogDO::getId));
        assertEquals(Integer.valueOf(21), logs.get(0).getOperateType());assertEquals(Integer.valueOf(12), logs.get(1).getOperateType());assertEquals(Integer.valueOf(35), logs.get(2).getOperateType());
        assertEquals(3, stock());verifyNoInteractions(payApi, refundApi);
    }

    @Test void pickupStillCollectsBeforePickupWithoutDispatchStep() {
        seed(2, 10, false);
        TradeOrderOfflineReceiptReqVO receipt = new TradeOrderOfflineReceiptReqVO();
        receipt.setId(ORDER);receipt.setAmount(1500);receipt.setMethod("cash");
        assertServiceException(() -> orders.pickUpOrderByAdmin(ADMIN, ORDER), OFFLINE_RECEIPT_INVALID);
        stores.confirmReceipt(receipt, ADMIN);
        assertEquals(10, orderMapper.selectById(ORDER).getStatus());assertTrue(orderMapper.selectById(ORDER).getPayStatus());
        orders.pickUpOrderByAdmin(ADMIN, ORDER);
        assertEquals(30, orderMapper.selectById(ORDER).getStatus());assertEquals(2, logMapper.selectListByOrderId(ORDER).size());
        assertEquals(3, stock());verifyNoInteractions(payApi, refundApi);
    }

    @Test void historicallyPaidPendingDeliveryKeepsActualReceipt() {
        seed(3, 10, true);stores.startDelivery(ORDER, ADMIN);
        TradeOrderDO current = orderMapper.selectById(ORDER);
        assertEquals(20, current.getStatus());assertTrue(current.getPayStatus());assertEquals(PAID_AT, current.getReceiptTime());
        stores.completeDelivery(ORDER, ADMIN);
        assertEquals(30, orderMapper.selectById(ORDER).getStatus());assertEquals(PAID_AT, orderMapper.selectById(ORDER).getReceiptTime());
        assertEquals(2, logMapper.selectListByOrderId(ORDER).size());verifyNoInteractions(payApi, refundApi);
    }

    @Test void concurrentCollectionWaitsForDispatchCommitAndDoesNotJumpTheStep() throws Exception {
        seed(3, 10, false);blocking.arm("dispatch");
        TradeOrderOfflineReceiptReqVO receipt = new TradeOrderOfflineReceiptReqVO();
        receipt.setId(ORDER);receipt.setAmount(1500);receipt.setMethod("cash");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> run(() -> stores.startDelivery(ORDER, ADMIN)));
            assertTrue(blocking.entered.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = executor.submit(() -> run(() -> stores.confirmReceipt(receipt, ADMIN)));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            blocking.release.countDown();assertTrue(first.get(5, TimeUnit.SECONDS));assertTrue(second.get(5, TimeUnit.SECONDS));
            TradeOrderDO current = orderMapper.selectById(ORDER);
            assertEquals(20, current.getStatus());assertTrue(current.getPayStatus());assertNotNull(current.getDeliveryTime());
            assertEquals(2, logMapper.selectListByOrderId(ORDER).size());assertEquals(3, stock());verifyNoInteractions(payApi, refundApi);
        } finally { blocking.release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    private void complete(int delivery) {
        if (delivery == 2) orders.pickUpOrderByAdmin(ADMIN, ORDER);
        else stores.completeDelivery(ORDER, ADMIN);
    }

    private boolean run(Runnable operation) {
        context(USER, 1);
        try { operation.run();return true; }
        catch (ServiceException expectedBusinessRejection) { return false; }
        finally { RequestContextHolder.resetRequestAttributes();TenantContextHolder.clear(); }
    }
}
