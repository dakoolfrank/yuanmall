<template>
  <view class="yd-page-container yd-page-container-paging">
    <!-- 顶部导航栏 -->
    <wd-navbar
      title="商品管理"
      left-arrow placeholder safe-area-inset-top fixed
      @click-left="handleBack"
    >
      <template #right>
        <button v-if="hasAccessByCodes(['product:category:query'])" class="category-entry" @click="openCategories">
          商品类别
        </button>
      </template>
    </wd-navbar>

    <!-- 搜索组件 -->
    <SearchForm :initial-category-id="categoryId" @search="handleQuery" @reset="handleReset" />

    <view class="product-filters">
      <view class="product-filters__row">
        <button v-for="tab in STATUS_TABS" :key="tab.value" class="filter-button" :class="{ active: statusFilter === tab.value && !recycleMode }" @click="selectStatus(tab.value)">
          {{ tab.label }}
        </button>
        <button class="recycle-button" :class="{ active: recycleMode }" @click="openRecycle">
          回收站
        </button>
      </view>
      <view v-if="!recycleMode" class="product-filters__row stock-filters">
        <text class="stock-label">库存</text>
        <button v-for="tab in STOCK_TABS" :key="tab.value" class="filter-button" :class="{ active: stockFilter === tab.value }" @click="selectStock(tab.value)">
          {{ tab.label }}
        </button>
      </view>
    </view>

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
      empty-view-text="暂无商品数据"
      @query="queryList"
    >
      <view class="product-list">
        <view
          v-for="item in list"
          :key="item.id"
          class="product-card"
          @click="handleDetail(item)"
        >
          <view class="flex items-start gap-20rpx">
            <view v-if="item.picUrl" class="shrink-0">
              <wd-img :src="item.picUrl" width="140rpx" height="140rpx" radius="8rpx" mode="aspectFill" />
            </view>
            <view class="min-w-0 flex-1">
              <view class="mb-12rpx flex items-start justify-between gap-16rpx">
                <view class="min-w-0 flex-1 truncate text-32rpx text-[#333] font-semibold">
                  {{ item.name || '-' }}
                </view>
              </view>
              <view class="mb-12rpx text-34rpx text-[#fa8c16] font-semibold">
                {{ formatDisplayMoney(item.price) }}
              </view>
              <view class="product-status" :class="{ 'product-status--empty': item.stock === 0 }">
                {{ statusText(item.status) }} · {{ stockText(item.stock) }}
              </view>
            </view>
          </view>
        </view>
      </view>
    </z-paging>

    <!-- 新增按钮 -->
    <wd-fab
      v-if="hasAccessByCodes(['product:spu:create'])"
      position="right-bottom"
      type="primary"
      :expandable="false"
      @click="handleAdd"
    />
  </view>
</template>

<script lang="ts" setup>
import { parseId } from '@/utils/id'

import type { ProductSpu } from '@/api/mall/product/spu'
import { onUnload } from '@dcloudio/uni-app'
import { onMounted, ref } from 'vue'
import { getProductSpuPage } from '@/api/mall/product/spu'
import { useAccess } from '@/hooks/useAccess'
import { formatDisplayMoney } from '@/utils/format'
import { navigateBackPlus } from '@/utils'
import SearchForm from './components/search-form.vue'

const props = defineProps<{ categoryId?: number | any }>()

definePage({
  style: {
    navigationBarTitleText: '',
    navigationStyle: 'custom',
  },
})

const STATUS_TABS = [
  { label: '全部', value: 'all' },
  { label: '已上架', value: 'listed' },
  { label: '已下架', value: 'unlisted' },
]
const STOCK_TABS = [
  { label: '全部', value: 'all' },
  { label: '缺货', value: 'empty' },
  { label: '不足（1–10件）', value: 'low' },
]
const statusFilter = ref('all')
const stockFilter = ref('all')
const recycleMode = ref(false)
let generation = 0
let sourcePage = 1
let sourceHasMore = true
let buffer: ProductSpu[] = []

function statusText(status?: number) {
  return status === 1 ? '已上架' : status === 0 ? '已下架' : status === -1 ? '回收站' : '状态未知'
}
function stockText(stock?: number) {
  if (stock == null)
    return '库存未知'
  if (stock === 0)
    return '缺货'
  return `剩余 ${stock} 件${stock <= 10 ? ' · 库存不足' : ''}`
}
function selectStatus(value: string) {
  statusFilter.value = value
  recycleMode.value = false
  reload()
}
function selectStock(value: string) {
  stockFilter.value = value
  reload()
}
function openRecycle() {
  recycleMode.value = true
  reload()
}

const { hasAccessByCodes } = useAccess()
const list = ref<ProductSpu[]>([]) // 列表数据
const pagingRef = ref<any>() // 分页组件引用
const queryParams = ref<Record<string, any>>({}) // 查询参数（搜索表单部分：name）
const categoryId = ref<number | undefined>(props.categoryId ? parseId(props.categoryId) : undefined) // 分类筛选（路由深链 + 搜索表单共用）

function openCategories() {
  uni.navigateTo({ url: '/pages-mall/product/category/index' })
}

/** 返回上一页 */
function handleBack() {
  navigateBackPlus()
}

/** 沿用后端分页，缓冲筛选后的结果，不在当前一页上过滤后假报总数。 */
async function queryList(pageNo: number, pageSize: number) {
  if (pageNo === 1) {
    generation++
    sourcePage = 1
    sourceHasMore = true
    buffer = []
  }
  const current = generation
  const recycle = recycleMode.value
  const status = statusFilter.value
  const stock = stockFilter.value
  const tabType = recycle ? 4 : stock === 'empty' ? 2 : stock === 'low' ? 3 : status === 'listed' ? 0 : status === 'unlisted' ? 1 : undefined
  let nextPage = sourcePage
  let more = sourceHasMore
  const pending = [...buffer]
  try {
    while (pending.length < pageSize && more) {
      const data = await getProductSpuPage({
        ...queryParams.value,
        tabType,
        categoryId: categoryId.value,
        pageNo: nextPage,
        pageSize: 100,
      })
      if (current !== generation)
        return
      pending.push(...data.list.filter((item) => {
        if (recycle)
          return item.status === -1
        if (item.status !== 0 && item.status !== 1)
          return false
        if (status === 'listed' && item.status !== 1)
          return false
        if (status === 'unlisted' && item.status !== 0)
          return false
        if (stock === 'empty')
          return item.stock === 0
        if (stock === 'low')
          return item.stock != null && item.stock > 0 && item.stock <= 10
        return true
      }))
      more = data.list.length > 0 && nextPage * 100 < data.total
      nextPage++
    }
    if (current !== generation)
      return
    const page = pending.splice(0, pageSize)
    sourcePage = nextPage
    sourceHasMore = more
    buffer = pending
    pagingRef.value?.completeByNoMore(page, !more && pending.length === 0)
  } catch {
    if (current === generation)
      pagingRef.value?.complete(false)
  }
}

/** 搜索按钮操作 */
function handleQuery(data: Record<string, any> = {}) {
  // categoryId 单独维护（与路由深链共用），其余进 queryParams
  const { categoryId: cid, ...rest } = data
  categoryId.value = cid
  queryParams.value = { ...rest }
  reload()
}

/** 重置按钮操作 */
function handleReset() {
  handleQuery()
}

/** 筛选、搜索及业务变更后从第一页重新读取。 */
function reload() {
  generation++
  sourcePage = 1
  sourceHasMore = true
  buffer = []
  pagingRef.value?.reload()
}

/** 新增商品 */
function handleAdd() {
  uni.navigateTo({ url: '/pages-mall/product/spu/form/index' })
}

/** 查看详情 */
function handleDetail(item: ProductSpu) {
  uni.navigateTo({ url: `/pages-mall/product/spu/detail/index?id=${item.id}` })
}

/** 初始化 */
onMounted(() => {
  uni.$on('mall:product-spu:reload', reload)
})

/** 卸载 */
onUnload(() => {
  generation++
  uni.$off('mall:product-spu:reload', reload)
})
</script>

<style scoped>
.product-filters {
  background: white;
  padding: 8px 16px 12px;
}
.product-filters__row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.stock-filters {
  margin-top: 12px;
}
.filter-button,
.recycle-button {
  margin: 0;
  padding: 7px 12px;
  background: transparent;
  color: #69746c;
  font-size: 15px;
  line-height: 1.5;
  border-radius: 8px;
}
.filter-button::after,
.recycle-button::after {
  border: none;
}
.filter-button.active {
  background: #e9f0e8;
  color: #2e644b;
  font-weight: 600;
}
.recycle-button {
  margin-left: auto;
  font-size: 13px;
}
.recycle-button.active {
  color: #2e644b;
  text-decoration: underline;
}
.stock-label {
  color: #89918a;
  font-size: 13px;
  margin-right: 2px;
}
.stock-filters .filter-button {
  font-size: 13px;
  padding: 5px 10px;
}
.product-list {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: 16px 16px 88px;
  box-sizing: border-box;
}
.product-card {
  margin-bottom: 12px;
  border-radius: 12px;
  background: white;
  padding: 16px;
}
.product-status {
  margin-top: 8px;
  color: #68766b;
  font-size: 14px;
  line-height: 1.6;
}
.product-status--empty {
  color: #b75936;
}
</style>

<style scoped>
.category-entry {
  padding: 0 4px;
  margin: 0;
  background: transparent;
  color: #2e644b;
  font-size: 14px;
  line-height: 32px;
}
.category-entry::after {
  border: none;
}
</style>
