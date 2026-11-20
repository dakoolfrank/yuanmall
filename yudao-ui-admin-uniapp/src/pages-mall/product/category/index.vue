<template>
  <view class="yd-page-container">
    <!-- 顶部导航栏 -->
    <wd-navbar
      title="商品分类"
      left-arrow placeholder safe-area-inset-top fixed
      @click-left="handleBack"
    />

    <!-- 搜索组件 -->
    <SearchForm @search="handleQuery" @reset="handleReset" />

    <!-- 当前层级分类列表 -->
    <scroll-view scroll-y class="min-h-0 flex-1">
      <view class="p-24rpx">
        <view
          v-for="item in currentList"
          :key="item.id"
          class="mb-24rpx overflow-hidden rounded-12rpx bg-white p-24rpx shadow-sm"
          @click="handleDetail(item)"
        >
          <view class="flex items-start gap-20rpx">
            <view class="min-w-0 flex-1">
              <view class="mb-12rpx flex items-start justify-between gap-16rpx">
                <view class="min-w-0 flex-1 truncate text-32rpx text-[#333] font-semibold">
                  {{ item.name || '-' }}
                </view>
                <wd-tag v-if="item.status === CommonStatusEnum.DISABLE" type="warning" variant="plain">
                  停用
                </wd-tag>
              </view>
              <view class="text-26rpx text-[#666]">
                排序：{{ item.sort ?? '-' }}
              </view>
            </view>
          </view>

          <view class="mt-16rpx flex items-center justify-end" @click.stop="handleViewSpu(item)">
            <text class="text-26rpx text-[#2e644b]">查看商品 →</text>
          </view>
          <text v-if="item.parentId" class="text-24rpx text-[#999]">历史子分类</text>
        </view>

        <!-- 空状态 -->
        <view v-if="!loading && currentList.length === 0" class="py-100rpx text-center">
          <wd-empty icon="content" tip="暂无分类数据" />
        </view>
      </view>
    </scroll-view>

    <!-- 新增按钮 -->
    <wd-fab
      v-if="hasAccessByCodes(['product:category:create'])"
      position="right-bottom"
      type="primary"
      :expandable="false"
      @click="handleAdd"
    />
  </view>
</template>

<script lang="ts" setup>
import type { ProductCategory } from '@/api/mall/product/category'
import { onUnload } from '@dcloudio/uni-app'
import { onMounted, ref } from 'vue'
import { getProductCategoryList } from '@/api/mall/product/category'
import { useAccess } from '@/hooks/useAccess'
import { navigateBackPlus } from '@/utils'
import { CommonStatusEnum } from '@/utils/constants'
import SearchForm from './components/search-form.vue'

definePage({
  style: {
    navigationBarTitleText: '',
    navigationStyle: 'custom',
  },
})

const { hasAccessByCodes } = useAccess()
const loading = ref(false) // 列表加载状态
const currentList = ref<ProductCategory[]>([]) // 平铺分类，保留历史记录供维护
const queryParams = ref<Record<string, any>>({}) // 查询参数
function handleBack() {
  navigateBackPlus('/pages-mall/product/spu/index')
}

/** 查询分类列表 */
async function getList() {
  loading.value = true
  try {
    const data = await getProductCategoryList({ ...queryParams.value })
    currentList.value = data
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
function handleQuery(data?: Record<string, any>) {
  queryParams.value = { ...data }
  getList()
}

/** 重置按钮操作 */
function handleReset() {
  handleQuery()
}

/** 新增分类 */
function handleAdd() {
  uni.navigateTo({ url: '/pages-mall/product/category/form/index' })
}

/** 查看详情 */
function handleDetail(item: ProductCategory) {
  uni.navigateTo({ url: `/pages-mall/product/category/detail/index?id=${item.id}` })
}

/** 查看该分类下的商品 */
function handleViewSpu(item: ProductCategory) {
  uni.navigateTo({ url: `/pages-mall/product/spu/index?categoryId=${item.id}` })
}

/** 初始化 */
onMounted(() => {
  getList()
  uni.$on('mall:product-category:reload', getList)
})

/** 卸载 */
onUnload(() => {
  uni.$off('mall:product-category:reload', getList)
})
</script>
