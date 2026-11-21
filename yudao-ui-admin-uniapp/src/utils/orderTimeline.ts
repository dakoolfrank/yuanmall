import type { TradeOrder } from '@/api/mall/trade/order'
import { DeliveryTypeEnum } from '@/utils/constants'
import { formatBeijingDateTime } from '@/utils/date'
import { formatDisplayMoney } from '@/utils/format'

interface OrderTimelineEntry {
  time: string
  label: string
  detail?: string
}

/** 结构化字段补充金额和时间，合并同一笔只会发生一次的业务操作。 */
export function buildOrderTimeline(order: TradeOrder): OrderTimelineEntry[] {
  const logs = (order.logs ?? []).flatMap((log) => {
    const label = log.content?.trim()
    return label ? [{ label, time: formatBeijingDateTime(log.createTime) }] : []
  })
  const entries: OrderTimelineEntry[] = []
  const addEvent = (
    time: string | undefined,
    label: string,
    logPattern: RegExp,
    options: { detail?: string, keepLogLabel?: boolean } = {},
  ) => {
    const index = logs.findIndex(log => logPattern.test(log.label))
    if (!time && index < 0)
      return
    const log = index < 0 ? undefined : logs.splice(index, 1)[0]
    entries.push({
      time: formatBeijingDateTime(time) || log?.time || '',
      label: options.keepLogLabel && log ? log.label : label,
      detail: options.detail,
    })
  }

  addEvent(order.createTime, '用户下单', /^用户下单$/)
  if (order.deliveryType === DeliveryTypeEnum.STORE) {
    addEvent(order.deliveryTime, '开始配送', /^店铺开始配送$/)
    addEvent(order.receiveTime, '确认送达', /^店铺确认送达$/)
  } else if (order.deliveryType === DeliveryTypeEnum.PICK_UP) {
    addEvent(order.receiveTime, '确认已取货', /^管理员自提收货$/)
  } else {
    addEvent(order.deliveryTime, '已发货', /^已发货(?:，快递公司：.*)?$/, { keepLogLabel: true })
    addEvent(order.receiveTime, '已收货', /^(?:用户已收货|到期未收货，系统自动确认收货)$/, { keepLogLabel: true })
  }

  if (order.receiptTime && order.receiptAmount != null) {
    const method = order.receiptMethod === 'cash' ? '现金' : order.receiptMethod === 'scan' ? '现场扫码' : ''
    addEvent(order.receiptTime, '已收款', /^管理员确认见面收款(?:，方式：.*)?$/, {
      detail: [method, formatDisplayMoney(order.receiptAmount)].filter(Boolean).join(' · '),
    })
  }
  if (order.offlineRefundTime && order.offlineRefundAmount != null) {
    const method = order.offlineRefundMethod === 'cash' ? '现金退回' : order.offlineRefundMethod === 'scan' ? '微信转回' : ''
    addEvent(order.offlineRefundTime, '已退款', /^管理员登记已线下退款(?:，方式：.*)?$/, {
      detail: [method, formatDisplayMoney(order.offlineRefundAmount)].filter(Boolean).join(' · '),
    })
  }
  addEvent(order.cancelTime, '取消订单', /^(?:取消订单|到期未支付，系统自动取消订单|订单全部售后，管理员自动取消订单)$/, { keepLogLabel: true })

  return [...entries, ...logs].sort((a, b) => b.time.localeCompare(a.time))
}
