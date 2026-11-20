<template>
  <view class="yd-page-container">
    <!-- 顶部导航栏 -->
    <wd-navbar
      title="订单详情"
      left-arrow placeholder safe-area-inset-top fixed
      @click-left="handleBack"
    />

    <!-- 详情内容 -->
    <scroll-view class="min-h-0 flex-1" scroll-y scroll-with-animation>
      <view v-if="formData" class="order-detail-content">
        <view class="order-card order-summary">
          <view class="order-summary-heading">
            <text class="order-status" :class="{ 'order-status-closed': formData.status === 40 }">{{ statusLabel }}</text>
            <text v-if="paymentDescription" class="order-payment">{{ paymentDescription }}</text>
          </view>
          <view class="order-money-line">
            <text>订单 <text class="order-total">{{ formatDisplayMoney(formData.payPrice) }}</text></text>
            <text v-if="formData.payStatus === true">{{ refundedAmount > 0 ? '原收' : '已收' }} {{ formatDisplayMoney(formData.receiptAmount ?? formData.payPrice) }}</text>
            <text v-if="refundedAmount > 0">已退 {{ formatDisplayMoney(refundedAmount) }}</text>
          </view>
          <view v-if="priceAdjustments.length" class="order-adjustments">
            <text v-for="entry in priceAdjustments" :key="entry.label">{{ entry.label }} {{ formatDisplayMoney(entry.amount) }}</text>
          </view>
          <view class="order-meta">
            <text>{{ formData.no || '-' }}</text>
            <text>{{ formatBeijingDateTime(formData.createTime) }}</text>
          </view>
        </view>

        <view class="order-card">
          <view class="order-section-title">
            商品
          </view>
          <view v-for="item in formData.items || []" :key="item.id" class="order-product">
            <wd-img v-if="item.picUrl" :src="item.picUrl" width="64px" height="64px" radius="8px" mode="aspectFill" enable-preview />
            <view v-else class="order-product-placeholder">
              商品
            </view>
            <view class="order-product-info">
              <text class="order-product-name">{{ item.spuName || '商品' }}</text>
              <text v-if="itemSpecification(item)" class="order-muted">{{ itemSpecification(item) }}</text>
              <text class="order-muted">数量 {{ item.count ?? 0 }}<text v-if="item.price != null"> · 单价 {{ formatDisplayMoney(item.price) }}</text></text>
            </view>
            <text v-if="item.payPrice != null" class="order-product-amount">{{ formatDisplayMoney(item.payPrice) }}</text>
          </view>
        </view>

        <view v-if="hasFulfillmentInfo" class="order-card">
          <view class="order-fulfillment-heading">
            <text class="order-section-title">{{ deliveryLabel }}</text>
            <text v-if="fulfillmentProgress && fulfillmentProgress !== statusLabel" class="order-muted">{{ fulfillmentProgress }}</text>
          </view>
          <view v-if="formData.receiverName || formData.receiverMobile" class="order-contact">
            <text v-if="formData.receiverName">{{ formData.receiverName }}</text>
            <text v-if="formData.receiverMobile">{{ formData.receiverMobile }}</text>
          </view>
          <text v-if="formData.deliveryType === DeliveryTypeEnum.PICK_UP" class="order-address">{{ pickUpStoreName || '自提门店信息待确认' }}</text>
          <text v-else-if="receiverAddress" class="order-address">{{ receiverAddress }}</text>
        </view>

        <view v-if="formData.userRemark?.trim() || formData.remark?.trim()" class="order-card order-notes">
          <view v-if="formData.userRemark?.trim()">
            <text class="order-muted">买家留言：</text>{{ formData.userRemark }}
          </view>
          <view v-if="formData.remark?.trim()">
            <text class="order-muted">商家备注：</text>{{ formData.remark }}
          </view>
        </view>

        <view class="order-card order-records">
          <view class="order-record-toggle" role="button" tabindex="0" :aria-expanded="recordsExpanded" @click="recordsExpanded = !recordsExpanded" @keydown.enter="recordsExpanded = !recordsExpanded" @keydown.space.prevent="recordsExpanded = !recordsExpanded">
            <text>操作记录</text>
            <text class="order-muted">{{ recordsExpanded ? '收起 −' : '展开 +' }}</text>
          </view>
          <view v-if="recordsExpanded" class="order-record-content">
            <view v-if="formData.receiptTime" class="order-record-entry">
              <text class="order-record-title">线下付款登记</text>
              <text>{{ formData.receiptMethod === 'cash' ? '现金' : '现场扫码' }} · {{ formatDisplayMoney(formData.receiptAmount) }}</text>
              <text class="order-muted">{{ formatBeijingDateTime(formData.receiptTime) }}<text v-if="formData.receiptOperatorId != null"> · 管理员 {{ formData.receiptOperatorId }}</text></text>
            </view>
            <view v-if="formData.offlineRefundTime" class="order-record-entry">
              <text class="order-record-title">线下退款登记</text>
              <text>{{ formData.offlineRefundMethod === 'cash' ? '现金退回' : '微信转回' }} · {{ formatDisplayMoney(formData.offlineRefundAmount) }}</text>
              <text class="order-muted">{{ formatBeijingDateTime(formData.offlineRefundTime) }}<text v-if="formData.offlineRefundOperatorId != null"> · 管理员 {{ formData.offlineRefundOperatorId }}</text></text>
            </view>
            <view v-if="formData.user?.nickname || formData.payOrderId || formData.terminal != null || formData.type != null" class="order-record-entry order-muted">
              <text v-if="formData.user?.nickname">买家：{{ formData.user.nickname }}</text>
              <text v-if="formData.terminal != null">来源：{{ getDictLabel(DICT_TYPE.TERMINAL, formData.terminal) || '未指定' }}</text>
              <text v-if="formData.type != null">类型：{{ getDictLabel(DICT_TYPE.TRADE_ORDER_TYPE, formData.type) || (formData.type === 0 ? '普通订单' : '其他订单') }}</text>
              <text v-if="formData.payOrderId">支付单号：{{ formData.payOrderId }}</text>
            </view>
            <view v-if="formData.deliveryTime || formData.receiveTime || formData.cancelTime || (formData.pickUpVerifyCode && formData.status === TradeOrderStatusEnum.UNDELIVERED) || formData.logisticsNo" class="order-record-entry order-muted">
              <text v-if="formData.deliveryTime">{{ formData.deliveryType === DeliveryTypeEnum.STORE ? '开始配送' : '发货' }}：{{ formatBeijingDateTime(formData.deliveryTime) }}</text>
              <text v-if="formData.receiveTime">{{ formData.deliveryType === DeliveryTypeEnum.PICK_UP ? '取货' : '送达' }}：{{ formatBeijingDateTime(formData.receiveTime) }}</text>
              <text v-if="formData.cancelTime">取消：{{ formatBeijingDateTime(formData.cancelTime) }}</text>
              <text v-if="formData.pickUpVerifyCode && formData.status === TradeOrderStatusEnum.UNDELIVERED">核销码：{{ formData.pickUpVerifyCode }}</text>
              <text v-if="formData.logisticsNo">物流单号：{{ formData.logisticsNo }}</text>
            </view>
            <view v-if="expressTracks.length" class="order-record-entry">
              <text class="order-record-title">物流轨迹</text>
              <text v-for="(item, index) in expressTracks" :key="index" class="order-muted">{{ item.time || item.createTime || '' }} {{ item.content || item.context || '' }}</text>
            </view>
            <view v-if="formData.logs?.length" class="order-record-entry">
              <text v-for="(log, index) in formData.logs" :key="index" class="order-log"><text class="order-muted">{{ formatBeijingDateTime(log.createTime) }}</text> {{ log.content || '-' }}</text>
            </view>
          </view>
        </view>
      </view>

      <wd-empty v-else icon="content" tip="订单不存在" />
    </scroll-view>

    <!-- 底部操作按钮：按状态/权限显式展示，每个动作各自的按钮 + 处理函数 -->
    <view v-if="formData && (canRemark || canPrice || canDelivery || canAddress || canPickUp || canStoreStart || canCollect || canStoreComplete || canRefund)" class="yd-detail-footer">
      <view class="yd-detail-footer-actions store-actions">
        <wd-button v-if="canStoreStart" class="flex-1" :disabled="submitting" @click="handleStoreAction('start')">
          开始配送
        </wd-button>
        <wd-button v-if="canCollect" class="flex-1" type="primary" :disabled="submitting" @click="openReceipt">
          已付款
        </wd-button>
        <wd-button v-if="canStoreComplete" class="flex-1" type="success" :disabled="submitting" @click="handleStoreAction('complete')">
          确认送达
        </wd-button>
        <wd-button v-if="canPickUp" class="flex-1" type="success" :loading="submitting" @click="handlePickUp">
          确认已取货
        </wd-button>
        <wd-button v-if="canRefund" class="flex-1" type="primary" :disabled="submitting" @click="openRefund">
          确认已退款
        </wd-button>
        <wd-button v-if="canPrice" class="flex-1" type="primary" @click="openPrice">
          改价
        </wd-button>
        <wd-button v-if="canDelivery" class="flex-1" type="primary" @click="openDelivery">
          发货
        </wd-button>
        <wd-button v-if="canAddress" class="flex-1" @click="openAddress">
          改地址
        </wd-button>
        <wd-button v-if="canRemark" class="flex-1" type="warning" @click="openRemark">
          备注
        </wd-button>
      </view>
    </view>

    <wd-popup v-model="receiptVisible" position="bottom" :z-index="20" closable custom-style="border-radius: 16px 16px 0 0;" @close="receiptVisible = false">
      <view class="receipt-dialog">
        <view class="receipt-title">
          标记已付款
        </view>
        <view>订单：{{ formData?.no }}</view>
        <view class="receipt-amount">
          应收 {{ formatDisplayMoney(formData?.payPrice) }}
        </view>
        <wd-radio-group v-model="receiptMethod" type="button">
          <wd-radio value="cash">
            现金
          </wd-radio><wd-radio value="scan">
            现场扫码
          </wd-radio>
        </wd-radio-group>
        <view class="receipt-note">
          店主收到现金或扫码款后，在此手动登记已付款。
        </view>
        <wd-checkbox v-model="receiptConfirmed">
          我已核实收到本单全额款项
        </wd-checkbox>
        <wd-button block :loading="submitting" :disabled="!receiptMethod || !receiptConfirmed || submitting" @click="handleReceipt">
          确认标记已付款
        </wd-button>
      </view>
    </wd-popup>

    <wd-popup v-model="refundVisible" position="bottom" :z-index="20" closable custom-style="border-radius: 16px 16px 0 0;" @close="refundVisible = false">
      <view class="receipt-dialog">
        <view class="receipt-title">
          确认已退款
        </view>
        <view>订单：{{ formData?.no }}</view>
        <view class="receipt-amount">
          应退 {{ formatDisplayMoney(formData?.payPrice) }}
        </view>
        <wd-radio-group v-model="refundMethod" type="button">
          <wd-radio value="cash">
            现金退回
          </wd-radio>
          <wd-radio value="scan">
            微信转回
          </wd-radio>
        </wd-radio-group>
        <view class="receipt-note">
          请先实际退回款项，再登记。本操作不会自动转钱，原收款记录会保留。
        </view>
        <wd-checkbox v-model="refundConfirmed">
          我已实际退回本单全额款项
        </wd-checkbox>
        <wd-button block :loading="submitting" :disabled="!refundMethod || !refundConfirmed || submitting" @click="handleRefund">
          确认登记已退款
        </wd-button>
      </view>
    </wd-popup>

    <!-- 修改备注 -->
    <wd-popup v-model="remarkVisible" position="bottom" closable custom-style="border-radius: 24rpx 24rpx 0 0;" @close="remarkVisible = false">
      <view class="p-24rpx">
        <view class="mb-24rpx text-32rpx text-[#333] font-semibold">
          修改备注
        </view>
        <wd-textarea v-model="remarkForm.remark" clearable :maxlength="2000" placeholder="请输入商家备注" />
        <view class="mt-24rpx flex gap-20rpx">
          <wd-button class="flex-1" variant="plain" @click="remarkVisible = false">
            取消
          </wd-button>
          <wd-button class="flex-1" type="primary" :loading="submitting" @click="handleRemark">
            确定
          </wd-button>
        </view>
      </view>
    </wd-popup>

    <!-- 订单改价 -->
    <wd-popup v-model="priceVisible" position="bottom" closable custom-style="border-radius: 24rpx 24rpx 0 0;" @close="priceVisible = false">
      <view class="p-24rpx">
        <view class="mb-24rpx text-32rpx text-[#333] font-semibold">
          订单改价
        </view>
        <view class="mb-12rpx flex items-center gap-16rpx">
          <text class="shrink-0 text-28rpx text-[#666]">订单调价</text>
          <wd-input-number v-model="priceForm.adjustPrice" :min="-999999" :step="0.01" :precision="2" />
        </view>
        <view class="text-24rpx text-[#999]">
          正数加价，负数减价（单位：元）
        </view>
        <view class="mt-24rpx flex gap-20rpx">
          <wd-button class="flex-1" variant="plain" @click="priceVisible = false">
            取消
          </wd-button>
          <wd-button class="flex-1" type="primary" :loading="submitting" @click="handlePrice">
            确定
          </wd-button>
        </view>
      </view>
    </wd-popup>

    <!-- 订单发货 -->
    <wd-popup v-model="deliveryVisible" position="bottom" closable custom-style="border-radius: 24rpx 24rpx 0 0;" @close="deliveryVisible = false">
      <view class="p-24rpx">
        <view class="mb-24rpx text-32rpx text-[#333] font-semibold">
          订单发货
        </view>
        <view class="mb-16rpx flex items-center gap-16rpx">
          <text class="w-160rpx shrink-0 text-28rpx text-[#666]">快递公司</text>
          <view class="min-w-0 flex-1" @click="expressPickerVisible = true">
            <wd-input :model-value="expressName" readonly placeholder="请选择快递公司" />
          </view>
        </view>
        <view class="flex items-center gap-16rpx">
          <text class="w-160rpx shrink-0 text-28rpx text-[#666]">快递单号</text>
          <wd-input v-model="deliveryForm.logisticsNo" class="min-w-0 flex-1" clearable placeholder="请输入快递单号" />
        </view>
        <view class="mt-24rpx flex gap-20rpx">
          <wd-button class="flex-1" variant="plain" @click="deliveryVisible = false">
            取消
          </wd-button>
          <wd-button class="flex-1" type="primary" :loading="submitting" @click="handleDelivery">
            确定
          </wd-button>
        </view>
      </view>
    </wd-popup>

    <!-- 快递公司选择器 -->
    <wd-picker
      v-model:visible="expressPickerVisible"
      :model-value="deliveryForm.logisticsId"
      :columns="expressList"
      label-key="name"
      value-key="id"
      @confirm="({ value }) => deliveryForm.logisticsId = Number(value[0])"
    />

    <!-- 修改地址 -->
    <wd-popup v-model="addressVisible" position="bottom" closable custom-style="border-radius: 24rpx 24rpx 0 0;" @close="addressVisible = false">
      <view class="p-24rpx">
        <view class="mb-24rpx text-32rpx text-[#333] font-semibold">
          修改地址
        </view>
        <wd-form :model="addressForm">
          <wd-cell-group border>
            <wd-form-item title="收件人" title-width="180rpx">
              <wd-input v-model="addressForm.receiverName" clearable placeholder="请输入收件人" />
            </wd-form-item>
            <wd-form-item title="手机号" title-width="180rpx">
              <wd-input v-model="addressForm.receiverMobile" clearable placeholder="请输入手机号" />
            </wd-form-item>
            <yd-tree-select
              v-model="addressForm.receiverAreaId"
              label="所在地区"
              label-width="180rpx"
              :data="areaTree"
              placeholder="请选择所在地区"
            />
            <wd-form-item title="详细地址" title-width="180rpx">
              <wd-textarea v-model="addressForm.receiverDetailAddress" clearable :maxlength="2000" placeholder="请输入详细地址" />
            </wd-form-item>
          </wd-cell-group>
        </wd-form>
        <view class="mt-24rpx flex gap-20rpx">
          <wd-button class="flex-1" variant="plain" @click="addressVisible = false">
            取消
          </wd-button>
          <wd-button class="flex-1" type="primary" :loading="submitting" @click="handleAddress">
            确定
          </wd-button>
        </view>
      </view>
    </wd-popup>
  </view>
</template>

<script lang="ts" setup>
import type { TradeOrder, TradeOrderItem } from '@/api/mall/trade/order'
import type { DeliveryExpress } from '@/api/mall/trade/delivery/express'
import { useDialog } from '@wot-ui/ui/components/wd-dialog'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { onHide } from '@dcloudio/uni-app'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  completeStoreDelivery,
  confirmOfflinePayment,
  confirmOfflineRefund,
  deliveryTradeOrder,
  getTradeOrder,
  getTradeOrderExpressTrackList,
  pickUpTradeOrder,
  startStoreDelivery,
  updateTradeOrderAddress,
  updateTradeOrderPrice,
  updateTradeOrderRemark,
} from '@/api/mall/trade/order'
import { getSimpleDeliveryExpressList } from '@/api/mall/trade/delivery/express'
import { getSimpleDeliveryPickUpStoreList } from '@/api/mall/trade/delivery/pick-up-store'
import { getAreaTree } from '@/api/system/area'
import { getDictLabel } from '@/hooks/useDict'
import { useAccess } from '@/hooks/useAccess'
import { useUserStore } from '@/store/user'
import { formatDisplayMoney, yuanToFen } from '@/utils/format'
import { navigateBackPlus } from '@/utils'
import { DeliveryTypeEnum, DICT_TYPE, TradeOrderStatusEnum } from '@/utils/constants'
import { formatBeijingDateTime } from '@/utils/date'
import { canMarkOfflineOrderPaid, canMarkOfflineOrderRefunded, orderPaymentDescription } from '@/utils/offlinePayment'

const props = defineProps<{ id?: number | any, markPaid?: string | number }>()

definePage({
  style: {
    navigationBarTitleText: '',
    navigationStyle: 'custom',
  },
})

const { hasAccessByCodes } = useAccess()
const userStore = useUserStore()
const toast = useToast()
const dialog = useDialog()
const detailId = computed(() => props.id != null && props.id !== '' ? props.id : undefined) // 订单编号（路由透传）
const formData = ref<TradeOrder>() // 订单详情
const expressTracks = ref<Record<string, any>[]>([]) // 物流轨迹
const areaTree = ref<any[]>([]) // 地区树（修改地址用）
const submitting = ref(false) // 动作提交状态

const recordsExpanded = ref(false)
const refundedAmount = computed(() => formData.value?.offlineRefundAmount ?? formData.value?.refundPrice ?? 0)
const receiverAddress = computed(() => [formData.value?.receiverAreaName, formData.value?.receiverDetailAddress].filter(Boolean).join(' ').trim())
const deliveryLabel = computed(() => ({ 1: '快递配送', 2: '到店自提', 3: '店铺配送' } as Record<number, string>)[formData.value?.deliveryType ?? 0] || '收货信息')
const hasFulfillmentInfo = computed(() => formData.value?.deliveryType === DeliveryTypeEnum.PICK_UP || !!(formData.value?.receiverName || formData.value?.receiverMobile || receiverAddress.value))
const fulfillmentProgress = computed(() => {
  const order = formData.value
  if (order?.status === TradeOrderStatusEnum.UNDELIVERED)
    return order.deliveryType === DeliveryTypeEnum.PICK_UP ? '待取货' : order.deliveryType === DeliveryTypeEnum.STORE ? '待配送' : '待发货'
  if (order?.status === TradeOrderStatusEnum.DELIVERED)
    return order.deliveryType === DeliveryTypeEnum.STORE ? '配送中' : '待收货'
  return ''
})
const statusLabel = computed(() => {
  const order = formData.value
  if (!order)
    return ''
  if (order.status === TradeOrderStatusEnum.CANCELED)
    return '已取消'
  if (order.status === TradeOrderStatusEnum.COMPLETED)
    return '已完成'
  if (order.paymentMethod === 2 && order.deliveryType !== DeliveryTypeEnum.STORE && (order.status === 10 || order.status === 20)) {
    if (order.deliveryType === DeliveryTypeEnum.PICK_UP && order.payStatus === true && order.status === 10)
      return '待取货'
    return order.payStatus === true ? '已付款' : order.payStatus === false ? '待收款' : '付款状态待确认'
  }
  return fulfillmentProgress.value || getDictLabel(DICT_TYPE.TRADE_ORDER_STATUS, order.status) || '状态待确认'
})
const paymentDescription = computed(() => {
  if (!formData.value)
    return ''
  return orderPaymentDescription(formData.value).split(' · ').filter(part => part !== '见面付款' && part !== statusLabel.value).join(' · ')
})
const priceAdjustments = computed(() => {
  const order = formData.value
  if (!order)
    return []
  return [
    { label: '运费', amount: order.deliveryPrice },
    { label: '优惠', amount: order.discountPrice },
    { label: '优惠券', amount: order.couponPrice },
    { label: '积分抵扣', amount: order.pointPrice },
    { label: '会员优惠', amount: order.vipPrice },
    { label: '调价', amount: order.adjustPrice },
  ].filter((entry): entry is { label: string, amount: number } => entry.amount != null && entry.amount !== 0)
})
function itemSpecification(item: TradeOrderItem) {
  const isDefault = (value?: string) => !value?.trim() || /^(?:默认|默认规格|默认值|default)$/i.test(value.trim())
  return (item.properties || []).filter(prop => !isDefault(prop.valueName)).map(prop => isDefault(prop.propertyName) ? prop.valueName : `${prop.propertyName}：${prop.valueName}`).join('；')
}

const remarkVisible = ref(false) // 备注弹窗
const priceVisible = ref(false) // 改价弹窗
const deliveryVisible = ref(false) // 发货弹窗
const addressVisible = ref(false) // 改地址弹窗
const remarkForm = reactive({ remark: '' }) // 备注表单
const priceForm = reactive({ adjustPrice: 0 }) // 改价表单（元）
const deliveryForm = reactive<{ logisticsId?: number, logisticsNo: string }>({ logisticsId: undefined, logisticsNo: '' }) // 发货表单
const addressForm = reactive<Record<string, any>>({ receiverName: '', receiverMobile: '', receiverAreaId: undefined, receiverDetailAddress: '' }) // 地址表单
const expressPickerVisible = ref(false) // 快递公司选择器
const expressList = ref<DeliveryExpress[]>([]) // 快递公司列表
const expressName = computed(() => expressList.value.find(item => item.id === deliveryForm.logisticsId)?.name || '') // 当前选中快递公司名
const pickUpStoreList = ref<Record<string, any>[]>([]) // 自提门店列表（解析门店名）
const pickUpStoreName = computed(() => pickUpStoreList.value.find(item => item.id === formData.value?.pickUpStoreId)?.name || '') // 自提门店名

// 各动作可见性：按权限 + 订单状态 + 配送方式判断
const canRemark = computed(() => !!formData.value && hasAccessByCodes(['trade:order:update'])) // 备注任意状态可改
const canPrice = computed(() => canRemark.value && formData.value?.status === TradeOrderStatusEnum.UNPAID) // 待付款可改价
const canDelivery = computed(() => canRemark.value && formData.value?.status === TradeOrderStatusEnum.UNDELIVERED && formData.value?.deliveryType === DeliveryTypeEnum.EXPRESS) // 待发货 + 快递
const canAddress = computed(() => canDelivery.value) // 与发货同条件
const canPickUp = computed(() => !!formData.value && hasAccessByCodes(['trade:order:pick-up']) && formData.value?.status === TradeOrderStatusEnum.UNDELIVERED && formData.value?.deliveryType === DeliveryTypeEnum.PICK_UP && formData.value.payStatus === true && (formData.value.paymentMethod !== 2 || formData.value.refundStatus === 0)) // 已收款、待取货的自提订单

const receiptVisible = ref(false)
const receiptMethod = ref<'cash' | 'scan' | ''>('')
const receiptConfirmed = ref(false)
const refundVisible = ref(false)
const refundMethod = ref<'cash' | 'scan' | ''>('')
const refundConfirmed = ref(false)
let refundIntent = 0
const canStoreStart = computed(() => canRemark.value && formData.value?.deliveryType === DeliveryTypeEnum.STORE && formData.value?.status === TradeOrderStatusEnum.UNDELIVERED && (formData.value.paymentMethod === 2 || formData.value.payStatus))
const canCollect = computed(() => hasAccessByCodes(['trade:order:confirm-offline-payment']) && canMarkOfflineOrderPaid(formData.value))
const canRefund = computed(() => hasAccessByCodes(['trade:order:confirm-offline-refund']) && canMarkOfflineOrderRefunded(formData.value))
const canStoreComplete = computed(() => canRemark.value && formData.value?.deliveryType === DeliveryTypeEnum.STORE && formData.value?.status === TradeOrderStatusEnum.DELIVERED && formData.value.payStatus)
function openReceipt() {
  if (!canCollect.value || submitting.value)
    return
  receiptMethod.value = ''
  receiptConfirmed.value = false
  receiptVisible.value = true
}
async function handleReceipt() {
  if (!canCollect.value || submitting.value || !receiptConfirmed.value || !receiptMethod.value || !formData.value?.id || formData.value.payPrice == null)
    return
  const data = { id: formData.value.id, amount: formData.value.payPrice, method: receiptMethod.value }
  submitting.value = true
  try {
    await dialog.confirm({ title: '标记已付款', msg: `确认你已在线下收到 ${formatDisplayMoney(data.amount)}（${data.method === 'cash' ? '现金' : '现场扫码'}）？保存后会登记为已付款，并记录操作管理员和时间。` })
    await confirmOfflinePayment(data)
    receiptVisible.value = false
    await afterSubmit()
  } catch { /* 取消或接口拒绝时保留输入，接口错误由统一请求层显示。 */ } finally { submitting.value = false }
}
function openRefund() {
  if (!canRefund.value || submitting.value)
    return
  refundMethod.value = ''
  refundConfirmed.value = false
  refundVisible.value = true
}
async function handleRefund() {
  if (!canRefund.value || submitting.value || !refundConfirmed.value || !refundMethod.value || !formData.value?.id || formData.value.payPrice == null)
    return
  const intent = ++refundIntent
  const data = { id: formData.value.id, amount: formData.value.payPrice, method: refundMethod.value }
  let attempted = false
  submitting.value = true
  try {
    await dialog.confirm({ title: '登记线下退款', msg: `确认已通过${data.method === 'cash' ? '现金' : '微信转回'}实际退回 ${formatDisplayMoney(data.amount)}？此处只登记，不自动转钱，保存后将记录管理员和时间。` })
    if (intent !== refundIntent)
      return
    await loadDetail()
    if (intent !== refundIntent || !canRefund.value || formData.value?.id !== data.id || formData.value.payPrice !== data.amount) {
      refundVisible.value = false
      toast.warning('订单已变化，请重新核对退款记录')
      return
    }
    attempted = true
    await confirmOfflineRefund(data)
    refundVisible.value = false
    await afterSubmit()
  } catch {
    if (attempted) {
      // 响应丢失时只重新查询，不假定失败、不自动重复登记。
      refundVisible.value = false
      await loadDetail()
      uni.$emit('mall:trade-order:reload')
    }
  } finally { submitting.value = false }
}
onHide(() => {
  refundIntent++
  refundVisible.value = false
})
async function handleStoreAction(action: 'start' | 'complete') {
  if (submitting.value || !formData.value?.id || (action === 'start' ? !canStoreStart.value : !canStoreComplete.value))
    return
  const id = formData.value.id
  submitting.value = true
  try {
    await dialog.confirm({ title: action === 'start' ? '开始配送' : '确认送达', msg: action === 'start' ? '确认安排店铺配送？见面付款收到钱后，请点“已付款”登记。' : '确认商品已送达顾客？' })
    await (action === 'start' ? startStoreDelivery(id) : completeStoreDelivery(id))
    await afterSubmit()
  } catch { /* 取消或失败时不改变本地订单状态。 */ } finally { submitting.value = false }
}

/** 返回上一页 */
function handleBack() {
  navigateBackPlus('/pages-mall/trade/order/index')
}

/** 加载详情 */
async function loadDetail() {
  if (!detailId.value) {
    return
  }
  formData.value = await getTradeOrder(detailId.value)
  expressTracks.value = formData.value.logisticsId ? await getTradeOrderExpressTrackList(detailId.value) : []
}

/** 提交后统一处理：成功提示 + 通知列表刷新 + 重载详情 */
async function afterSubmit() {
  toast.success('操作成功')
  uni.$emit('mall:trade-order:reload')
  await loadDetail()
}

/** 打开备注弹窗 */
function openRemark() {
  remarkForm.remark = formData.value?.remark || ''
  remarkVisible.value = true
}

/** 修改备注 */
async function handleRemark() {
  if (!remarkForm.remark?.trim()) {
    toast.warning('请输入商家备注')
    return
  }
  submitting.value = true
  try {
    await updateTradeOrderRemark({ id: detailId.value, remark: remarkForm.remark })
    remarkVisible.value = false
    await afterSubmit()
  } finally {
    submitting.value = false
  }
}

/** 打开改价弹窗 */
function openPrice() {
  priceForm.adjustPrice = 0
  priceVisible.value = true
}

/** 订单改价 */
async function handlePrice() {
  if (submitting.value) {
    return
  }
  const adjustPrice = yuanToFen(priceForm.adjustPrice)
  if (adjustPrice === 0) {
    toast.warning('请输入本次调价金额')
    return
  }
  submitting.value = true
  try {
    await updateTradeOrderPrice({ id: detailId.value, adjustPrice })
    priceVisible.value = false
    await afterSubmit()
  } finally {
    submitting.value = false
  }
}

/** 打开发货弹窗 */
function openDelivery() {
  deliveryForm.logisticsId = formData.value?.logisticsId
  deliveryForm.logisticsNo = formData.value?.logisticsNo || ''
  deliveryVisible.value = true
}

/** 订单发货 */
async function handleDelivery() {
  if (!deliveryForm.logisticsId) {
    toast.warning('请选择快递公司')
    return
  }
  if (!deliveryForm.logisticsNo?.trim()) {
    toast.warning('请输入快递单号')
    return
  }
  submitting.value = true
  try {
    await deliveryTradeOrder({ id: detailId.value, logisticsId: deliveryForm.logisticsId ?? null, logisticsNo: deliveryForm.logisticsNo })
    deliveryVisible.value = false
    await afterSubmit()
  } finally {
    submitting.value = false
  }
}

/** 打开改地址弹窗 */
function openAddress() {
  addressForm.receiverName = formData.value?.receiverName || ''
  addressForm.receiverMobile = formData.value?.receiverMobile || ''
  addressForm.receiverAreaId = formData.value?.receiverAreaId
  addressForm.receiverDetailAddress = formData.value?.receiverDetailAddress || ''
  addressVisible.value = true
}

/** 修改地址 */
async function handleAddress() {
  if (!addressForm.receiverName?.trim() || !addressForm.receiverMobile?.trim() || addressForm.receiverAreaId == null || !addressForm.receiverDetailAddress?.trim()) {
    toast.warning('请完善收货信息')
    return
  }
  submitting.value = true
  try {
    await updateTradeOrderAddress({ id: detailId.value, ...addressForm })
    addressVisible.value = false
    await afterSubmit()
  } finally {
    submitting.value = false
  }
}

/** 订单核销（自提） */
async function handlePickUp() {
  if (!canPickUp.value || submitting.value)
    return
  try {
    await dialog.confirm({ title: '确认已取货', msg: '确认已将商品交给顾客？确认后订单将完成。' })
  } catch {
    return
  }
  submitting.value = true
  try {
    await pickUpTradeOrder(detailId.value)
    await afterSubmit()
  } finally {
    submitting.value = false
  }
}

/** 初始化 */
onMounted(async () => {
  await Promise.all([
    // 权限持久化可能早于收款功能上线；以当前服务器授权更新按钮，自动打开收款也须等待权限同步。
    Promise.all([loadDetail(), userStore.fetchUserInfo()]).then(() => {
      if (String(props.markPaid) === '1')
        openReceipt()
    }),
    getAreaTree().then((list) => {
      areaTree.value = list
    }),
    getSimpleDeliveryExpressList().then((list) => {
      expressList.value = list
    }),
    getSimpleDeliveryPickUpStoreList().then((list) => {
      pickUpStoreList.value = list
    }),
  ])
})
</script>

<style scoped>
.order-detail-content {
  width: 100%;
  max-width: 1080px;
  box-sizing: border-box;
  margin: 0 auto;
  padding: 16px 16px calc(96px + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: #26352d;
  font-size: 14px;
  line-height: 1.5;
}
.order-card {
  padding: 20px;
  border-radius: 14px;
  background: #fff;
}
.order-summary-heading,
.order-fulfillment-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.order-status {
  color: #37634c;
  font-size: 22px;
  font-weight: 600;
}
.order-status-closed {
  color: #758078;
}
.order-payment {
  color: #536b5c;
}
.order-money-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 20px;
  margin-top: 12px;
}
.order-total {
  font-size: 22px;
  font-weight: 600;
}
.order-meta,
.order-adjustments {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 12px;
  color: #7d877f;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.order-section-title {
  font-size: 16px;
  font-weight: 600;
}
.order-product {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 16px;
}
.order-product-placeholder {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #f4f6f3;
  color: #8a958d;
  font-size: 12px;
}
.order-product-info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-wrap: anywhere;
}
.order-product-name {
  font-size: 15px;
  font-weight: 500;
}
.order-product-amount {
  flex-shrink: 0;
  font-weight: 600;
}
.order-muted {
  color: #7d877f;
  font-size: 12px;
}
.order-contact {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 12px;
}
.order-address {
  display: block;
  margin-top: 6px;
  overflow-wrap: anywhere;
}
.order-notes {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-wrap: anywhere;
}
.order-records {
  padding-top: 0;
  padding-bottom: 0;
}
.order-record-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 0;
  cursor: pointer;
}
.order-record-toggle:focus-visible {
  outline: 2px solid #37634c;
  outline-offset: 4px;
}
.order-record-content {
  border-top: 1px solid #edf0eb;
  padding: 4px 0 16px;
  overflow-wrap: anywhere;
}
.order-record-entry {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0;
}
.order-record-entry + .order-record-entry {
  border-top: 1px solid #edf0eb;
}
.order-record-title {
  font-weight: 500;
}
.order-log {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
}
@media (max-width: 600px) {
  .order-detail-content {
    padding-right: 12px;
    padding-left: 12px;
  }
  .order-card {
    padding-right: 16px;
    padding-left: 16px;
  }
  .order-summary-heading {
    align-items: flex-start;
  }
  .order-payment {
    font-size: 12px;
  }
  .order-money-line {
    gap: 4px 14px;
    font-size: 13px;
  }
}
.store-actions {
  flex-wrap: wrap;
}
.receipt-dialog {
  padding: 24px 20px;
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 18px;
  font-size: 14px;
}
.receipt-title {
  font-size: 18px;
  font-weight: 600;
}
.receipt-amount {
  font-size: 24px;
  color: #2e644b;
}
.receipt-note {
  color: #778174;
  line-height: 1.6;
}
</style>
