package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderOfflineReceiptReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

class TradeStoreOrderServiceTest extends BaseMockitoUnitTest {
    @InjectMocks private TradeStoreOrderService service;
    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeOrderItemMapper itemMapper;
    @Mock private TradeOrderHandler handler;
    private TradeOrderDO order;

    @BeforeEach void prepare() {
        ReflectionTestUtils.setField(service, "handlers", Collections.singletonList(handler));
        order = new TradeOrderDO().setId(9L).setDeliveryType(3).setPaymentMethod(2).setType(0)
                .setStatus(20).setPayStatus(false).setPayPrice(1200).setRefundStatus(0).setUserId(10L);
        when(orderMapper.selectForUpdate(9L)).thenReturn(order);
    }
    private TradeOrderOfflineReceiptReqVO request(String method, int amount) {
        TradeOrderOfflineReceiptReqVO req = new TradeOrderOfflineReceiptReqVO();
        req.setId(9L); req.setMethod(method); req.setAmount(amount); return req;
    }
    @Test void receiptAuditsExactAmountAndNeverCreatesOnlinePayment() {
        when(orderMapper.updateById(any(TradeOrderDO.class))).thenReturn(1);
        when(itemMapper.selectListByOrderId(9L)).thenReturn(Collections.emptyList());
        service.confirmReceipt(request("cash", 1200), 7L);
        verify(orderMapper).updateById(argThat((TradeOrderDO row) -> Boolean.TRUE.equals(row.getPayStatus())
                && row.getReceiptAmount() == 1200 && row.getReceiptOperatorId() == 7L
                && "cash".equals(row.getReceiptMethod()) && "offline_cash".equals(row.getPayChannelCode())
                && row.getPayOrderId() == null && row.getReceiptTime() != null && row.getStatus() == null));
        verify(handler).afterPayOrder(eq(order), anyList());
        assertEquals(20, order.getStatus());
    }
    @Test void duplicateReceiptNeverRunsPaymentHooksAgain() {
        order.setPayStatus(true);
        assertServiceException(() -> service.confirmReceipt(request("scan", 1200), 7L), OFFLINE_RECEIPT_CONFLICT);
        verify(orderMapper, never()).updateById(any(TradeOrderDO.class)); verifyNoInteractions(handler);
    }
    @Test void rejectsWrongAmount() {
        assertServiceException(() -> service.confirmReceipt(request("cash", 1199), 7L), OFFLINE_RECEIPT_INVALID);
        verify(orderMapper, never()).updateById(any(TradeOrderDO.class));
    }
    @Test void rejectsOnlineOrdersAndForgedMethods() {
        order.setPaymentMethod(1);
        assertServiceException(() -> service.confirmReceipt(request("cash", 1200), 7L), OFFLINE_RECEIPT_INVALID);
        order.setPaymentMethod(2);
        assertServiceException(() -> service.confirmReceipt(request("wx_lite", 1200), 7L), OFFLINE_RECEIPT_INVALID);
    }
    @Test void receiptBeforeDispatchKeepsDeliveryPending() {
        order.setStatus(10);
        when(orderMapper.updateById(any(TradeOrderDO.class))).thenReturn(1);
        when(itemMapper.selectListByOrderId(9L)).thenReturn(Collections.emptyList());
        service.confirmReceipt(request("scan", 1200), 7L);
        verify(orderMapper).updateById(argThat((TradeOrderDO row) -> Boolean.TRUE.equals(row.getPayStatus())
                && row.getReceiptAmount() == 1200 && row.getReceiptOperatorId() == 7L
                && "scan".equals(row.getReceiptMethod()) && "offline_scan".equals(row.getPayChannelCode())
                && row.getPayOrderId() == null && row.getStatus() == null && row.getDeliveryTime() == null));
        assertEquals(10, order.getStatus());
        verify(handler).afterPayOrder(eq(order), anyList());
    }
    @Test void rejectsCanceledCompletedUncreatedAndExpressOrders() {
        for (int status : new int[] {0, 30, 40}) {
            order.setStatus(status);
            assertServiceException(() -> service.confirmReceipt(request("cash", 1200), 7L), OFFLINE_RECEIPT_INVALID);
        }
        order.setStatus(20).setDeliveryType(1);
        assertServiceException(() -> service.confirmReceipt(request("cash", 1200), 7L), OFFLINE_ORDER_INVALID);
        verify(orderMapper, never()).updateById(any(TradeOrderDO.class));
    }
    @Test void dispatchAllowsUnpaidOfflineButRejectsUnpaidOnline() {
        order.setStatus(10);
        when(orderMapper.updateByIdAndStatus(eq(9L), eq(10), any())).thenReturn(1);
        service.startDelivery(9L, 7L);
        assertEquals(20, order.getStatus()); assertFalse(order.getPayStatus());
        order.setStatus(10).setPaymentMethod(1);
        assertServiceException(() -> service.startDelivery(9L, 7L), OFFLINE_ORDER_INVALID);
    }
    @Test void completionRequiresCollectionAndLeavesReceiptUnchanged() {
        assertServiceException(() -> service.completeDelivery(9L, 7L), OFFLINE_RECEIPT_INVALID);
        order.setPayStatus(true).setReceiptMethod("scan").setReceiptAmount(1200);
        when(orderMapper.updateByIdAndStatus(eq(9L), eq(20), any())).thenReturn(1);
        service.completeDelivery(9L, 7L);
        assertEquals(30, order.getStatus()); assertEquals(1200, order.getReceiptAmount());
        verify(handler).afterReceiveOrder(order);
    }
    @Test void tenantFilteredMissingOrderDoesNotUpdate() {
        when(orderMapper.selectForUpdate(9L)).thenReturn(null);
        assertServiceException(() -> service.confirmReceipt(request("cash", 1200), 7L), ORDER_NOT_FOUND);
        verify(orderMapper, never()).updateById(any(TradeOrderDO.class));
    }
}
