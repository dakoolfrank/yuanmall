<template>
  <view class="member-orders min-h-0 flex flex-1 flex-col">
    <OrderSearchForm @search="handleQuery" @reset="handleReset" />
    <z-paging
      ref="pagingRef"
      v-model="list"
      :fixed="false"
      class="min-h-0 flex-1"
      :default-page-size="10"
      :refresher-enabled="true"
      :inside-more="true"
      :loading-more-default-as-loading="true"
      empty-view-text="暂无订单"
      @query="queryList"
    >
      <view class="member-orders__list">
        <view v-for="item in list" :key="item.id" class="member-order" @click="handleDetail(item)">
          <view class="member-order__heading">
            <text class="member-order__number">{{ item.no || `订单 ${item.id}` }}</text>
            <text class="member-order__status">{{ orderStatus(item) }}</text>
          </view>
          <text class="member-order__product">{{ item.items?.map(goods => `${goods.spuName || '商品'} ×${goods.count ?? 1}`).join('、') || `共 ${item.productCount ?? 0} 件商品` }}</text>
          <view class="member-order__summary">
            <text class="member-order__amount">{{ formatDisplayMoney(item.payPrice) }}</text>
            <text class="member-order__delivery">{{ deliveryDescription(item) }}</text>
          </view>
          <text v-if="paymentDescription(item)" class="member-order__payment">{{ paymentDescription(item) }}</text>
          <view class="member-order__footer">
            <text>{{ formatBeijingDateTime(item.createTime) || '-' }}</text>
            <text class="member-order__link">查看订单 ›</text>
          </view>
        </view>
      </view>
    </z-paging>
  </view>
</template>

<script lang="ts" setup>
import type { TradeOrder } from '@/api/mall/trade/order'
import { ref, watch } from 'vue'
import { getTradeOrderPage } from '@/api/mall/trade/order'
import { formatBeijingDateTime } from '@/utils/date'
import { formatDisplayMoney } from '@/utils/format'
import { orderPaymentDescription } from '@/utils/offlinePayment'
import OrderSearchForm from './order-search-form.vue'

const props = defineProps<{ userId?: number | null }>()
const list = ref<TradeOrder[]>([])
const pagingRef = ref<ZPagingRef<TradeOrder>>()
const queryParams = ref<Record<string, any>>({})

function deliveryName(type?: number) {
  return ({ 1: '快递', 2: '自提', 3: '店铺配送' } as Record<number, string>)[type ?? 0] || '未指定配送方式'
}

function deliveryProgress(order: TradeOrder) {
  if (order.status === 10) {
    return order.deliveryType === 2 ? '待取货' : order.deliveryType === 3 ? '待配送' : '待发货'
  }
  if (order.status === 20) {
    return order.deliveryType === 3 ? '配送中' : '待收货'
  }
  return ({ 0: '待付款', 30: '已完成', 40: '已取消' } as Record<number, string>)[order.status ?? -1] || '状态未知'
}

function orderStatus(order: TradeOrder) {
  if (order.paymentMethod === 2 && order.deliveryType !== 3 && (order.status === 10 || order.status === 20)) {
    if (order.deliveryType === 2 && order.payStatus) {
      return '待取货'
    }
    return order.payStatus ? '已付款' : '待收款'
  }
  return deliveryProgress(order)
}

function deliveryDescription(order: TradeOrder) {
  return [deliveryName(order.deliveryType), deliveryProgress(order)]
    .filter(part => part !== orderStatus(order))
    .join(' · ')
}

function paymentDescription(order: TradeOrder) {
  return orderPaymentDescription(order).split(' · ').filter(part => part !== '见面付款' && part !== orderStatus(order)).join(' · ')
}

async function queryList(pageNo: number, pageSize: number) {
  if (!props.userId) {
    pagingRef.value?.complete([])
    return
  }
  try {
    const data = await getTradeOrderPage({ ...queryParams.value, userId: props.userId, pageNo, pageSize })
    pagingRef.value?.completeByTotal(data.list, data.total)
  } catch {
    pagingRef.value?.complete(false)
  }
}

function handleQuery(data?: Record<string, any>) {
  queryParams.value = { ...data }
  reload()
}

function handleReset() {
  handleQuery()
}

function reload() {
  pagingRef.value?.reload()
}

function handleDetail(item: TradeOrder) {
  if (item.id) {
    uni.navigateTo({ url: `/pages-mall/trade/order/detail/index?id=${item.id}` })
  }
}

watch(() => props.userId, reload)
</script>

<style scoped>
.member-orders__list {
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
}
.member-order {
  margin-bottom: 12px;
  padding: 20px;
  border: 1px solid #e4e9e1;
  border-radius: 12px;
  background: #fff;
}
.member-order__heading,
.member-order__summary,
.member-order__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.member-order__number {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #788378;
  font-size: 13px;
}
.member-order__status {
  flex-shrink: 0;
  color: #3d624c;
  font-size: 14px;
}
.member-order__product {
  display: block;
  margin: 14px 0;
  overflow-wrap: anywhere;
  color: #233a2d;
  font-size: 17px;
  font-weight: 600;
}
.member-order__amount {
  flex-shrink: 0;
  color: #a06333;
  font-size: 22px;
}
.member-order__delivery {
  color: #46634e;
  font-size: 14px;
  text-align: right;
}
.member-order__payment {
  display: block;
  margin-top: 10px;
  color: #788378;
  font-size: 14px;
}
.member-order__footer {
  margin-top: 16px;
  color: #8b9487;
  font-size: 12px;
}
.member-order__link {
  flex-shrink: 0;
  color: #46634e;
}
@media (max-width: 480px) {
  .member-orders__list {
    padding: 12px;
  }
  .member-order {
    padding: 16px 14px;
  }
}
</style>
