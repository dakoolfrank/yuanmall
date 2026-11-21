<template>
  <view class="yd-page-container yd-page-container-paging">
    <!-- 顶部导航栏 -->
    <wd-navbar
      :title="memberScoped ? '会员订单' : '订单管理'"
      left-arrow placeholder safe-area-inset-top fixed
      @click-left="handleBack"
    />

    <view class="order-categories">
      <view class="order-categories__items">
        <button
          v-for="category in categories"
          :key="category.label"
          class="order-category"
          :class="{ 'order-category--active': selectedStatus === category.status }"
          :aria-pressed="selectedStatus === category.status"
          @click="selectCategory(category.status)"
        >
          {{ category.label }}
        </button>
      </view>
    </view>

    <!-- 搜索组件 -->
    <wd-search v-if="memberScoped" v-model="memberOrderNo" placeholder="搜索该会员的订单号" hide-cancel @search="handleMemberOrderSearch" @clear="handleMemberOrderSearch" />
    <SearchForm v-else :status="selectedStatus" @search="handleQuery" @reset="handleReset" />

    <!-- 分页列表 -->
    <z-paging
      ref="pagingRef"
      v-model="list"
      :fixed="false"
      class="min-h-0 flex-1"
      :default-page-size="10"
      :refresher-enabled="true"
      :inside-more="true"
      :loading-more-default-as-loading="true"
      empty-view-text="暂无订单数据"
      @query="queryList"
    >
      <view class="p-24rpx">
        <view
          v-for="item in list"
          :key="item.id"
          class="order-list-card mb-24rpx overflow-hidden rounded-12rpx bg-white p-24rpx shadow-sm"
          @click="handleDetail(item)"
        >
          <view class="mb-16rpx flex items-start justify-between gap-16rpx">
            <view class="min-w-0 flex-1 truncate text-30rpx text-[#333] font-semibold">
              {{ item.no || '-' }}
            </view>
            <view class="flex shrink-0 items-center gap-12rpx">
              <wd-tag v-if="item.status === 40" type="default" variant="plain">
                {{ orderStatusLabel(item) }}
              </wd-tag>
              <wd-tag v-else-if="item.deliveryType === 3 && (item.status === 10 || item.status === 20)" type="primary" variant="plain">
                {{ orderStatusLabel(item) }}
              </wd-tag>
              <wd-tag v-else-if="item.deliveryType === 2 && item.status === 10" type="primary" variant="plain">
                {{ orderStatusLabel(item) }}
              </wd-tag>
              <wd-tag v-else-if="item.paymentMethod === 2 && item.payStatus && (item.status === 10 || item.status === 20)" type="success">
                {{ orderStatusLabel(item) }}
              </wd-tag>
              <dict-tag v-else-if="item.status != null" :type="DICT_TYPE.TRADE_ORDER_STATUS" :value="item.status" />
            </view>
          </view>

          <view class="mb-16rpx text-36rpx text-[#fa8c16] font-semibold">
            {{ formatDisplayMoney(item.payPrice) }}
          </view>

          <view v-if="item.deliveryType === 2 || item.deliveryType === 3 || item.paymentMethod === 2" class="order-card-description mb-12rpx text-28rpx text-[#46634e]">
            {{ orderDescription(item) }}
          </view>
          <view class="mb-12rpx flex items-center text-28rpx text-[#666]">
            <text class="mr-8rpx shrink-0 text-[#999]">订单类型：</text>
            <text>{{ item.type != null ? getDictLabel(DICT_TYPE.TRADE_ORDER_TYPE, item.type) : '-' }}</text>
          </view>
          <view class="mb-12rpx flex items-center text-28rpx text-[#666]">
            <text class="mr-8rpx shrink-0 text-[#999]">下单用户：</text>
            <text class="truncate">{{ item.user?.nickname || item.receiverName || '-' }}</text>
          </view>
          <view class="flex items-center text-28rpx text-[#666]">
            <text class="mr-8rpx shrink-0 text-[#999]">下单时间：</text>
            <text>{{ formatBeijingDateTime(item.createTime) || '-' }}</text>
          </view>
        </view>
      </view>
    </z-paging>
  </view>
</template>

<script lang="ts" setup>
import type { TradeOrder } from '@/api/mall/trade/order'
import { onUnload } from '@dcloudio/uni-app'
import { computed, onMounted, ref } from 'vue'
import { getTradeOrderPage } from '@/api/mall/trade/order'
import { getDictLabel } from '@/hooks/useDict'
import { useUserStore } from '@/store/user'
import { formatDisplayMoney } from '@/utils/format'
import { navigateBackPlus } from '@/utils'
import { DICT_TYPE, TradeOrderStatusEnum } from '@/utils/constants'
import { formatBeijingDateTime } from '@/utils/date'
import { orderPaymentDescription } from '@/utils/offlinePayment'
import { isPositiveId, parseId } from '@/utils/id'
import SearchForm from './components/search-form.vue'

const props = defineProps<{ userId?: string | number }>()

definePage({
  style: {
    navigationBarTitleText: '',
    navigationStyle: 'custom',
  },
})

const memberScoped = computed(() => props.userId != null)
const memberId = computed(() => parseId(props.userId))
const categories = [
  { label: '全部', status: undefined },
  { label: '待处理', status: TradeOrderStatusEnum.UNDELIVERED },
  { label: '配送中', status: TradeOrderStatusEnum.DELIVERED },
  { label: '已完成', status: TradeOrderStatusEnum.COMPLETED },
  { label: '已取消', status: TradeOrderStatusEnum.CANCELED },
]
const selectedStatus = ref<number | undefined>(memberScoped.value ? undefined : TradeOrderStatusEnum.UNDELIVERED)
const memberOrderNo = ref('')
const list = ref<TradeOrder[]>([]) // 列表数据
const pagingRef = ref<any>() // 分页组件引用
const queryParams = ref<Record<string, any>>({}) // 查询参数
const userStore = useUserStore()
let queryVersion = 0

function selectCategory(status?: number) {
  if (selectedStatus.value === status)
    return
  selectedStatus.value = status
  reload()
}

/** 右上角标签和说明行使用同一状态文案，避免重复显示。 */
function orderStatusLabel(order: TradeOrder) {
  if (order.status === 40)
    return '已取消'
  if (order.deliveryType === 3 && (order.status === 10 || order.status === 20))
    return order.status === 10 ? '待配送' : '配送中'
  if (order.deliveryType === 2 && order.status === 10)
    return order.payStatus ? '待取货' : '待收款'
  if (order.paymentMethod === 2 && order.payStatus && (order.status === 10 || order.status === 20))
    return '已付款'
  return getDictLabel(DICT_TYPE.TRADE_ORDER_STATUS, order.status) || ''
}

function orderDescription(order: TradeOrder) {
  const delivery = order.deliveryType === 2 ? '到店自提' : order.deliveryType === 3 ? '店铺配送' : '快递配送'
  const badge = orderStatusLabel(order)
  return [delivery, ...orderPaymentDescription(order).split(' · ')]
    .filter(part => part !== badge)
    .join(' · ')
}

/** 返回上一页 */
function handleBack() {
  navigateBackPlus()
}

/** 查询订单列表 */
async function queryList(pageNo: number, pageSize: number) {
  const version = ++queryVersion
  if (memberScoped.value && !isPositiveId(memberId.value)) {
    pagingRef.value?.complete(false)
    uni.showToast({ title: '会员编号无效，请返回重试', icon: 'none' })
    return
  }
  try {
    const data = await getTradeOrderPage({
      ...queryParams.value,
      status: selectedStatus.value,
      ...(memberScoped.value ? { userId: memberId.value } : {}),
      pageNo,
      pageSize,
    })
    if (version === queryVersion)
      pagingRef.value?.completeByTotal(data.list, data.total)
  } catch {
    if (version === queryVersion)
      pagingRef.value?.complete(false)
  }
}

function handleMemberOrderSearch() {
  const no = memberOrderNo.value.trim()
  handleQuery(no ? { no } : undefined)
}

/** 搜索按钮操作 */
function handleQuery(data?: Record<string, any>) {
  const { status, ...filters } = data || {}
  if (data && 'status' in data)
    selectedStatus.value = status
  queryParams.value = filters
  reload()
}

/** 重置按钮操作 */
function handleReset() {
  handleQuery()
}

/** 重新加载 */
function reload() {
  queryVersion++
  pagingRef.value?.reload().catch(() => {})
}

/** 查看详情 */
function handleDetail(item: TradeOrder) {
  uni.navigateTo({ url: `/pages-mall/trade/order/detail/index?id=${item.id}` })
}

/** 初始化 */
onMounted(async () => {
  uni.$on('mall:trade-order:reload', reload)
  await userStore.fetchUserInfo()
})

/** 卸载 */
onUnload(() => {
  queryVersion++
  uni.$off('mall:trade-order:reload', reload)
})
</script>

<style scoped>
.order-categories {
  flex-shrink: 0;
  padding: 4px 8px;
  background: #fff;
  border-bottom: 1px solid #e7ebe4;
}
.order-categories__items {
  display: flex;
  max-width: 580px;
}
.order-category {
  flex: 1 1 0;
  min-width: 0;
  margin: 0;
  padding: 0 4px;
  border-radius: 8px;
  background: transparent;
  color: #58675c;
  font-size: 14px;
  line-height: 44px;
  white-space: nowrap;
}
.order-category::after {
  border: 0;
}
.order-category--active {
  background: #eaf1e9;
  color: #284d39;
  font-weight: 600;
}
</style>
