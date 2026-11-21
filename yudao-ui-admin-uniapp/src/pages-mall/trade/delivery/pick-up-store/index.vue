<template>
  <view class="yd-page-container yd-page-container-paging settings-page">
    <!-- 顶部导航栏 -->
    <wd-navbar
      title="设置"
      left-arrow placeholder safe-area-inset-top fixed
      @click-left="handleBack"
    />

    <z-paging
      ref="pagingRef"
      v-model="list"
      :fixed="false"
      class="min-h-0 flex-1"
      :default-page-size="10"
      :refresher-enabled="true"
      :inside-more="true"
      :loading-more-default-as-loading="true"
      :hide-no-more-by-limit="1"
      hide-empty-view
      @query="queryList"
    >
      <view class="settings-content">
        <StoreDeliveryRange />

        <view class="pickup-card">
          <view class="pickup-card__header">
            <view class="pickup-card__title">
              自提点
            </view>
            <wd-button
              v-if="hasAccessByCodes(['trade:delivery:pick-up-store:create'])"
              variant="text" size="small" custom-style="color: #2e644b;"
              @click="handleAdd"
            >
              新增自提点
            </wd-button>
          </view>

          <SearchForm v-if="showSearch" @search="handleQuery" @reset="handleReset" />

          <view v-if="firstPageFailed" class="pickup-empty">
            <text>自提点加载失败</text>
            <wd-button variant="text" size="small" custom-style="color: #2e644b;" @click="reload">
              重试
            </wd-button>
          </view>
          <view v-else-if="total === 0" class="pickup-empty">
            {{ showSearch ? '未找到符合条件的自提点' : '暂无自提点' }}
          </view>
          <view v-else-if="total == null" class="pickup-empty">
            正在加载…
          </view>

          <view v-for="item in list" :key="item.id" class="pickup-row">
            <view class="pickup-row__heading">
              <button class="pickup-row__name" @click="handleDetail(item)">
                {{ item.name || '未命名自提点' }} <text class="pickup-row__arrow">›</text>
              </button>
              <wd-tag v-if="item.status === CommonStatusEnum.DISABLE" type="warning" variant="plain">
                {{ isPickUpStoreIncomplete(item) ? '待完善' : '停用' }}
              </wd-tag>
              <wd-button
                v-if="hasAccessByCodes(['trade:delivery:pick-up-store:update'])"
                variant="text" size="small" custom-style="color: #2e644b; margin-left: auto;"
                @click="handleEdit(item)"
              >
                编辑
              </wd-button>
            </view>
            <view class="pickup-row__address">
              {{ getStoreAddress(item) || '地址待补充' }}
            </view>
            <view class="pickup-row__meta">
              <text>电话 {{ item.phone || '待补充' }}</text>
              <text>营业时间 {{ item.openingTime && item.closingTime ? `${item.openingTime}–${item.closingTime}` : '待补充' }}</text>
            </view>
          </view>
        </view>
      </view>
    </z-paging>
  </view>
</template>

<script lang="ts" setup>
import type { DeliveryPickUpStore } from '@/api/mall/trade/delivery/pick-up-store'
import { onUnload } from '@dcloudio/uni-app'
import { onMounted, ref } from 'vue'
import { getDeliveryPickUpStorePage } from '@/api/mall/trade/delivery/pick-up-store'
import { useAccess } from '@/hooks/useAccess'
import { navigateBackPlus } from '@/utils'
import { CommonStatusEnum } from '@/utils/constants'
import { isPickUpStoreIncomplete } from '@/utils/pickUpStore'
import SearchForm from './components/search-form.vue'
import StoreDeliveryRange from './components/store-delivery-range.vue'

definePage({
  style: {
    navigationBarTitleText: '',
    navigationStyle: 'custom',
  },
})

const { hasAccessByCodes } = useAccess()
const list = ref<DeliveryPickUpStore[]>([]) // 列表数据
const pagingRef = ref<any>() // 分页组件引用
const queryParams = ref<Record<string, any>>({}) // 查询参数
const total = ref<number>()
const showSearch = ref(false)
const firstPageFailed = ref(false)

/** 返回上一页 */
function handleBack() {
  navigateBackPlus()
}

/** 查询自提门店列表 */
async function queryList(pageNo: number, pageSize: number) {
  if (pageNo === 1)
    firstPageFailed.value = false
  try {
    const data = await getDeliveryPickUpStorePage({ ...queryParams.value, pageNo, pageSize })
    total.value = data.total
    if (pageNo === 1 && Object.keys(queryParams.value).length === 0)
      showSearch.value = data.total > 1
    pagingRef.value?.completeByTotal(data.list, data.total)
  } catch {
    if (pageNo === 1)
      firstPageFailed.value = true
    pagingRef.value?.complete(false)
  }
}

/** 搜索按钮操作 */
function handleQuery(data?: Record<string, any>) {
  queryParams.value = { ...data }
  reload()
}

/** 重置按钮操作 */
function handleReset() {
  handleQuery()
}

/** 重新加载 */
function reload() {
  pagingRef.value?.reload()
}

/** 新增自提门店 */
function handleAdd() {
  uni.navigateTo({ url: '/pages-mall/trade/delivery/pick-up-store/form/index' })
}

/** 查看详情 */
function handleDetail(item: DeliveryPickUpStore) {
  uni.navigateTo({ url: `/pages-mall/trade/delivery/pick-up-store/detail/index?id=${item.id}` })
}

function handleEdit(item: DeliveryPickUpStore) {
  uni.navigateTo({ url: `/pages-mall/trade/delivery/pick-up-store/form/index?id=${item.id}` })
}

function getStoreAddress(item: DeliveryPickUpStore) {
  const area = item.areaName?.replace(/^(\S+)\s+\1(?=\s|$)/, '$1')
  return [area, item.detailAddress].filter(Boolean).join(' ')
}

/** 初始化 */
onMounted(() => {
  uni.$on('mall:delivery-pick-up-store:reload', reload)
})

/** 卸载 */
onUnload(() => {
  uni.$off('mall:delivery-pick-up-store:reload', reload)
})
</script>

<style scoped>
.settings-page {
  background: #f2f5f0;
}

.settings-content {
  display: grid;
  gap: 14px;
  box-sizing: border-box;
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
  padding: 18px 16px 28px;
}

.pickup-card {
  padding: 14px 20px;
  border: 1px solid #e3e9e3;
  border-radius: 14px;
  background: #fff;
  color: #26382e;
}

.pickup-card__header,
.pickup-row__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.pickup-card__title {
  font-size: 17px;
  font-weight: 600;
}

.pickup-row {
  margin-top: 6px;
  padding: 9px 0 5px;
  border-top: 1px solid #e8ede8;
}

.pickup-row__name {
  min-width: 0;
  margin: 0;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: #26382e;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  text-align: left;
  overflow-wrap: anywhere;
}

.pickup-row__name::after {
  border: 0;
}

.pickup-row__heading {
  justify-content: flex-start;
}

.pickup-row__arrow {
  color: #2e644b;
}

.pickup-row__address,
.pickup-row__meta,
.pickup-empty {
  color: #58675d;
  font-size: 14px;
  line-height: 1.5;
}

.pickup-row__address {
  margin-top: 5px;
  overflow-wrap: anywhere;
}

.pickup-row__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 18px;
  margin-top: 5px;
}

.pickup-empty {
  padding: 10px 0 5px;
}

@media (max-width: 480px) {
  .settings-content {
    padding: 12px 10px 24px;
  }

  .pickup-card {
    padding: 10px 14px 14px;
  }
}
</style>
