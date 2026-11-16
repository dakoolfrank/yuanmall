import type { TradeOrder } from '@/api/mall/trade/order'
import { DeliveryTypeEnum, TradeOrderStatusEnum } from '@/utils/constants'

/** 仅供管理员登记见面付款，不发起在线支付。权限由调用页面和后端分别校验。 */
export function canMarkOfflineOrderPaid(order?: TradeOrder | null) {
  return !!order?.id
    && order.deliveryType === DeliveryTypeEnum.STORE
    && order.paymentMethod === 2
    && order.payStatus === false
    && order.payOrderId == null
    && order.receiptTime == null
    && (order.status === TradeOrderStatusEnum.UNDELIVERED || order.status === TradeOrderStatusEnum.DELIVERED)
}
