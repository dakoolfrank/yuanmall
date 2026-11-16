package cn.iocoder.yudao.module.trade.service.order;

import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderOfflineReceiptReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.*;
import cn.iocoder.yudao.module.trade.framework.order.core.annotations.TradeOrderLog;
import cn.iocoder.yudao.module.trade.framework.order.core.utils.TradeOrderLogUtils;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

/** 店铺配送及线下收款。所有操作锁定当前租户订单行，不修改在线支付单。 */
@Service
public class TradeStoreOrderService {
    @Resource private TradeOrderMapper orderMapper;
    @Resource private TradeOrderItemMapper itemMapper;
    @Resource private List<TradeOrderHandler> handlers;

    private TradeOrderDO lockStoreOrder(Long id) {
        TradeOrderDO order = orderMapper.selectForUpdate(id);
        if (order == null) throw exception(ORDER_NOT_FOUND);
        if (!DeliveryTypeEnum.STORE.getType().equals(order.getDeliveryType())
                || !Integer.valueOf(0).equals(order.getRefundStatus())) throw exception(OFFLINE_ORDER_INVALID);
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_STORE_DELIVERY)
    public void startDelivery(Long id, Long adminId) {
        TradeOrderDO order = lockStoreOrder(id);
        if (!TradeOrderStatusEnum.UNDELIVERED.getStatus().equals(order.getStatus())
                || (!Integer.valueOf(2).equals(order.getPaymentMethod()) && !Boolean.TRUE.equals(order.getPayStatus()))) {
            throw exception(OFFLINE_ORDER_INVALID);
        }
        handlers.forEach(handler -> handler.beforeDeliveryOrder(order));
        LocalDateTime now = LocalDateTime.now();
        int count = orderMapper.updateByIdAndStatus(id, order.getStatus(), new TradeOrderDO()
                .setStatus(TradeOrderStatusEnum.DELIVERED.getStatus()).setDeliveryTime(now)
                .setLogisticsId(0L).setLogisticsNo(""));
        if (count != 1) throw exception(OFFLINE_RECEIPT_CONFLICT);
        audit(order, adminId, TradeOrderStatusEnum.DELIVERED.getStatus());
        order.setStatus(TradeOrderStatusEnum.DELIVERED.getStatus()).setDeliveryTime(now).setLogisticsId(0L).setLogisticsNo("");
        handlers.forEach(handler -> handler.afterDeliveryOrder(order));
    }

    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_OFFLINE_RECEIPT)
    public void confirmReceipt(TradeOrderOfflineReceiptReqVO request, Long adminId) {
        TradeOrderDO order = lockStoreOrder(request.getId());
        if (!Integer.valueOf(2).equals(order.getPaymentMethod()) || order.getPayOrderId() != null
                || !(TradeOrderStatusEnum.UNDELIVERED.getStatus().equals(order.getStatus())
                     || TradeOrderStatusEnum.DELIVERED.getStatus().equals(order.getStatus()))
                || !Objects.equals(order.getPayPrice(), request.getAmount()) || request.getAmount() == null || request.getAmount() <= 0
                || !("cash".equals(request.getMethod()) || "scan".equals(request.getMethod()))) {
            throw exception(OFFLINE_RECEIPT_INVALID);
        }
        if (Boolean.TRUE.equals(order.getPayStatus()) || order.getReceiptTime() != null) throw exception(OFFLINE_RECEIPT_CONFLICT);
        LocalDateTime now = LocalDateTime.now();
        TradeOrderDO update = new TradeOrderDO().setId(order.getId()).setPayStatus(true).setPayTime(now)
                .setPayChannelCode("offline_" + request.getMethod()).setReceiptMethod(request.getMethod())
                .setReceiptAmount(request.getAmount()).setReceiptOperatorId(adminId).setReceiptTime(now);
        if (orderMapper.updateById(update) != 1) throw exception(OFFLINE_RECEIPT_CONFLICT);
        // 已有营销/积分后置流程只执行一次；事务失败全部回滚，不伪造支付回调。
        List<TradeOrderItemDO> items = itemMapper.selectListByOrderId(order.getId());
        order.setPayStatus(true).setPayTime(now).setPayChannelCode(update.getPayChannelCode());
        handlers.forEach(handler -> handler.afterPayOrder(order, items));
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), order.getStatus(), MapUtil.<String,Object>builder()
                .put("receiptMethod", "cash".equals(request.getMethod()) ? "现金" : "现场扫码")
                .put("receiptAmount", request.getAmount()).build());
        TradeOrderLogUtils.setUserInfo(adminId, UserTypeEnum.ADMIN.getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_STORE_RECEIVE)
    public void completeDelivery(Long id, Long adminId) {
        TradeOrderDO order = lockStoreOrder(id);
        if (!TradeOrderStatusEnum.DELIVERED.getStatus().equals(order.getStatus()) || !Boolean.TRUE.equals(order.getPayStatus())) {
            throw exception(OFFLINE_RECEIPT_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        if (orderMapper.updateByIdAndStatus(id, order.getStatus(), new TradeOrderDO()
                .setStatus(TradeOrderStatusEnum.COMPLETED.getStatus()).setReceiveTime(now)) != 1) {
            throw exception(OFFLINE_RECEIPT_CONFLICT);
        }
        audit(order, adminId, TradeOrderStatusEnum.COMPLETED.getStatus());
        order.setStatus(TradeOrderStatusEnum.COMPLETED.getStatus()).setReceiveTime(now);
        handlers.forEach(handler -> handler.afterReceiveOrder(order));
    }

    private void audit(TradeOrderDO order, Long adminId, Integer nextStatus) {
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), nextStatus);
        TradeOrderLogUtils.setUserInfo(adminId, UserTypeEnum.ADMIN.getValue());
    }
}
