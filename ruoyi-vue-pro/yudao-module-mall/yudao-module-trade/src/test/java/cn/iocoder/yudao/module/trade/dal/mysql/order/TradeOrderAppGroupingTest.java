package cn.iocoder.yudao.module.trade.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderPageItemRespVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Executes real mapper SQL against H2; payment and fulfilment groups may overlap. */
class TradeOrderAppGroupingTest extends BaseDbUnitTest {

    private static final Long USER_ID = 10L;

    @Resource
    private TradeOrderMapper mapper;

    @BeforeEach
    void seedOrders() {
        insert(1, USER_ID, 0, false, 3, 1, false);
        insert(2, USER_ID, 10, false, 3, 2, false);
        insert(3, USER_ID, 10, true, 3, 2, false);
        insert(4, USER_ID, 20, false, 3, 2, false);
        insert(5, USER_ID, 20, true, 3, 2, false);
        insert(6, USER_ID, 30, true, 3, 2, false);
        insert(7, USER_ID, 40, false, 3, 2, false);
        insert(8, USER_ID, 30, false, 3, 2, false);
        insert(9, 11L, 10, false, 3, 2, false);
        insert(10, USER_ID, 10, false, 3, 2, true);
        insert(11, USER_ID, 10, false, 2, 2, false);
        insert(12, USER_ID, 10, true, 2, 2, false);
        insert(13, USER_ID, 0, true, 3, 1, false);
    }

    @Test
    void unpaidListAndCountOverlapWithDispatchPickupAndReceipt() {
        assertGroup(0, Arrays.asList(11L, 4L, 2L, 1L));
        assertGroup(10, Arrays.asList(12L, 11L, 3L, 2L));
        assertGroup(20, Arrays.asList(5L, 4L));
        assertEquals(11L, mapper.selectCountByUserIdAndStatus(USER_ID, null, null));
        assertEquals(11L, page(null, 1, 100).getTotal());
    }

    @Test
    void recordingPaymentRemovesOnlyPaymentMembership() {
        for (long id : new long[]{2, 4, 11}) {
            mapper.updateById(TradeOrderDO.builder().id(id).payStatus(true).build());
        }
        assertGroup(0, Arrays.asList(1L));
        assertGroup(10, Arrays.asList(12L, 11L, 3L, 2L));
        assertGroup(20, Arrays.asList(5L, 4L));
        assertEquals(10, mapper.selectById(11L).getStatus());
        assertEquals(20, mapper.selectById(4L).getStatus());
        assertEquals(1000, mapper.selectById(2L).getPayPrice());
    }

    @Test
    void pagingAndUserAndLogicalDeletionScopesRemainEffective() {
        PageResult<TradeOrderDO> secondPage = page(0, 2, 2);
        assertEquals(4L, secondPage.getTotal());
        assertEquals(Arrays.asList(2L, 1L), ids(secondPage));
        assertEquals(1L, mapper.selectCountByUserIdAndStatus(11L, 0, null));
        assertEquals(Arrays.asList(9L), ids(mapper.selectPage(request(0, 1, 100), 11L)));
    }

    @Test
    void completedAndCancelledNeverAppearAsAwaitingPayment() {
        assertFalse(ids(page(0, 1, 100)).contains(7L));
        assertFalse(ids(page(0, 1, 100)).contains(8L));
        assertGroup(30, Arrays.asList(8L, 6L));
        assertGroup(40, Arrays.asList(7L));
    }

    @Test
    void commentFilterUsesSameConditionsForCountsAndPages() {
        mapper.updateById(TradeOrderDO.builder().id(6L).commentStatus(true).build());
        AppTradeOrderPageReqVO req = request(30, 1, 100);
        req.setCommentStatus(false);
        assertEquals(Arrays.asList(8L), ids(mapper.selectPage(req, USER_ID)));
        assertEquals(1L, mapper.selectCountByUserIdAndStatus(USER_ID, 30, false));
    }

    @Test
    void listResponseKeepsOriginalStatusAndExposesIndependentPaymentFlag() {
        PageResult<AppTradeOrderPageItemRespVO> response = TradeOrderConvert.INSTANCE.convertPage02(
                page(0, 1, 100), Collections.emptyList());
        AppTradeOrderPageItemRespVO pickup = response.getList().get(0);
        assertEquals(11L, pickup.getId());
        assertEquals(10, pickup.getStatus());
        assertEquals(2, pickup.getDeliveryType());
        assertEquals(2, pickup.getPaymentMethod());
        assertFalse(pickup.getPayStatus());
        assertNull(pickup.getPayOrderId());
        assertEquals(1000, pickup.getPayPrice());
    }

    private void assertGroup(Integer status, List<Long> expected) {
        PageResult<TradeOrderDO> page = page(status, 1, 100);
        assertEquals(expected, ids(page));
        assertEquals((long) expected.size(), page.getTotal());
        assertEquals(page.getTotal(), mapper.selectCountByUserIdAndStatus(USER_ID, status, null));
    }

    private PageResult<TradeOrderDO> page(Integer status, int pageNo, int pageSize) {
        return mapper.selectPage(request(status, pageNo, pageSize), USER_ID);
    }

    private AppTradeOrderPageReqVO request(Integer status, int pageNo, int pageSize) {
        AppTradeOrderPageReqVO request = new AppTradeOrderPageReqVO();
        request.setStatus(status);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return request;
    }

    private List<Long> ids(PageResult<TradeOrderDO> page) {
        return page.getList().stream().map(TradeOrderDO::getId).collect(Collectors.toList());
    }

    private void insert(long id, Long userId, int status, boolean paid, int deliveryType,
                        int paymentMethod, boolean deleted) {
        TradeOrderDO order = TradeOrderDO.builder().id(id).no("test-" + id).type(0).terminal(30)
                .userId(userId).userIp("127.0.0.1").status(status).productCount(1).commentStatus(false)
                .payStatus(paid).paymentMethod(paymentMethod).totalPrice(1000).discountPrice(0)
                .deliveryPrice(0).adjustPrice(0).payPrice(1000).deliveryType(deliveryType)
                .receiverName("test").receiverMobile("13900000000").receiverAreaId(110113)
                .receiverDetailAddress("test").couponId(0L).couponPrice(0).pointPrice(0).build();
        order.setDeleted(deleted);
        mapper.insert(order);
    }
}
