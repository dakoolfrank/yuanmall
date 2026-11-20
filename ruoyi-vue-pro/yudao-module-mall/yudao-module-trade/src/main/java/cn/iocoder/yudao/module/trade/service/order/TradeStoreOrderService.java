package cn.iocoder.yudao.module.trade.service.order;

import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderOfflineReceiptReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderOfflineRefundReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.*;
import cn.iocoder.yudao.module.trade.framework.order.core.annotations.TradeOrderLog;
import cn.iocoder.yudao.module.trade.framework.order.core.utils.TradeOrderLogUtils;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import cn.iocoder.yudao.module.trade.service.order.bo.TradeOrderLogCreateReqBO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

/** 店铺配送及配送/自提的线下收款。所有操作锁定当前租户订单行，不修改在线支付单。 */
@Service
public class TradeStoreOrderService {
    @Resource private TradeOrderMapper orderMapper;
    @Resource private TradeOrderItemMapper itemMapper;
    @Resource private List<TradeOrderHandler> handlers;
    @Resource private TradeOrderLogService orderLogService;

    private TradeOrderDO lockLocalOrder(Long id) {
        TradeOrderDO order = orderMapper.selectForUpdate(id);
        if (order == null) throw exception(ORDER_NOT_FOUND);
        if ((!DeliveryTypeEnum.STORE.getType().equals(order.getDeliveryType())
             && !DeliveryTypeEnum.PICK_UP.getType().equals(order.getDeliveryType()))
                || !Integer.valueOf(0).equals(order.getRefundStatus())) throw exception(OFFLINE_ORDER_INVALID);
        return order;
    }

    private TradeOrderDO lockStoreOrder(Long id) {
        TradeOrderDO order = lockLocalOrder(id);
        if (!DeliveryTypeEnum.STORE.getType().equals(order.getDeliveryType())) throw exception(OFFLINE_ORDER_INVALID);
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
        TradeOrderDO order = lockLocalOrder(request.getId());
        // 店铺配送必须先开始配送；自提没有配送步骤，仍在待取货阶段收款。
        Integer requiredStatus = DeliveryTypeEnum.PICK_UP.getType().equals(order.getDeliveryType())
                ? TradeOrderStatusEnum.UNDELIVERED.getStatus() : TradeOrderStatusEnum.DELIVERED.getStatus();
        if (!TradeOrderTypeEnum.NORMAL.getType().equals(order.getType())
                || !Integer.valueOf(2).equals(order.getPaymentMethod()) || order.getPayOrderId() != null
                || !requiredStatus.equals(order.getStatus())
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

    /** 取消时已经处理库存和营销；这里只登记真实线下退款，不能再次执行取消/支付后置处理。 */
    @Transactional(rollbackFor = Exception.class)
    public void confirmRefund(TradeOrderOfflineRefundReqVO request, Long adminId) {
        TradeOrderDO order = orderMapper.selectForUpdate(request.getId());
        if (order == null) throw exception(ORDER_NOT_FOUND);
        if (order.getOfflineRefundTime() != null || order.getOfflineRefundAmount() != null) {
            throw exception(OFFLINE_REFUND_CONFLICT);
        }
        if (!TradeOrderTypeEnum.NORMAL.getType().equals(order.getType())
                || !Integer.valueOf(2).equals(order.getPaymentMethod()) || order.getPayOrderId() != null
                || !(DeliveryTypeEnum.STORE.getType().equals(order.getDeliveryType())
                     || DeliveryTypeEnum.PICK_UP.getType().equals(order.getDeliveryType()))
                || !TradeOrderStatusEnum.CANCELED.getStatus().equals(order.getStatus())
                || !Boolean.TRUE.equals(order.getPayStatus())
                || !TradeOrderRefundStatusEnum.NONE.getStatus().equals(order.getRefundStatus())
                || (order.getRefundPrice() != null && order.getRefundPrice() != 0)
                || request.getAmount() == null || request.getAmount() <= 0
                || !Objects.equals(order.getPayPrice(), request.getAmount())
                || !("cash".equals(request.getMethod()) || "scan".equals(request.getMethod()))) {
            throw exception(OFFLINE_REFUND_INVALID);
        }
        TradeOrderDO update = new TradeOrderDO().setId(order.getId())
                .setOfflineRefundMethod(request.getMethod()).setOfflineRefundAmount(request.getAmount())
                .setOfflineRefundOperatorId(adminId).setOfflineRefundTime(LocalDateTime.now())
                .setRefundStatus(TradeOrderRefundStatusEnum.ALL.getStatus()).setRefundPrice(request.getAmount());
        if (orderMapper.updateById(update) != 1) throw exception(OFFLINE_REFUND_CONFLICT);
        // 退款登记与审计必须同事务成功；通用日志切面会吞掉错误，此处显式写入以保证失败回滚。
        orderLogService.createOrderLog(new TradeOrderLogCreateReqBO().setOrderId(order.getId())
                .setBeforeStatus(order.getStatus()).setAfterStatus(order.getStatus())
                .setUserId(adminId).setUserType(UserTypeEnum.ADMIN.getValue())
                .setOperateType(TradeOrderOperateTypeEnum.ADMIN_OFFLINE_REFUND.getType())
                .setContent("管理员登记已线下退款，方式：" + ("cash".equals(request.getMethod()) ? "现金退回" : "微信转回")
                        + "，金额：" + request.getAmount() + " 分"));
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
