import type { TradeOrder } from '@/api/mall/trade/order'
import { DeliveryTypeEnum, TradeOrderStatusEnum } from '@/utils/constants'

/** 仅供管理员登记见面付款，不发起在线支付。权限由调用页面和后端分别校验。 */
export function canMarkOfflineOrderPaid(order?: TradeOrder | null) {
  return !!order?.id
    && (order.deliveryType === DeliveryTypeEnum.STORE || order.deliveryType === DeliveryTypeEnum.PICK_UP)
    && order.type === 0
    && order.refundStatus === 0
    && order.paymentMethod === 2
    && order.payStatus === false
    && order.payOrderId == null
    && order.receiptTime == null
    && (order.deliveryType === DeliveryTypeEnum.PICK_UP
      ? order.status === TradeOrderStatusEnum.UNDELIVERED
      : order.status === TradeOrderStatusEnum.DELIVERED)
}

/** 已取消订单的退款是独立记录，不把原已付款标志改为未付款。 */
export function canMarkOfflineOrderRefunded(order?: TradeOrder | null) {
  return !!order?.id
    && order.paymentMethod === 2
    && order.type === 0
    && (order.deliveryType === DeliveryTypeEnum.STORE || order.deliveryType === DeliveryTypeEnum.PICK_UP)
    && order.status === TradeOrderStatusEnum.CANCELED
    && order.payStatus === true
    && order.payOrderId == null
    && order.refundStatus === 0
    && (order.refundPrice == null || order.refundPrice === 0)
    && order.offlineRefundTime == null
    && order.offlineRefundAmount == null
    && order.payPrice != null && order.payPrice > 0
}

export function orderPaymentDescription(order: TradeOrder) {
  const method = order.paymentMethod === 2 ? '见面付款' : '在线支付'
  if (order.paymentMethod === 2 && order.status === TradeOrderStatusEnum.CANCELED) {
    if (order.offlineRefundTime != null || (order.refundStatus === 20 && (order.refundPrice ?? 0) >= (order.payPrice ?? 1)))
      return `${method} · 已退款`
    if (order.payStatus === true)
      return `${method} · 已付款${(order.payPrice ?? 0) > 0 ? ' · 待退款' : ''}`
    return `${method} · ${order.payStatus === false ? '未付款' : '付款状态待确认'}`
  }
  if (order.payStatus === true)
    return `${method} · 已付款`
  return `${method} · ${order.payStatus === false ? (order.paymentMethod === 2 && (order.status === 10 || order.status === 20) ? '待收款' : '未付款') : '付款状态待确认'}`
}
