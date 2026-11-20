<template>
  <view class="yd-page-container">
    <!-- 顶部导航栏 -->
    <wd-navbar
      title="分类详情"
      left-arrow placeholder safe-area-inset-top fixed
      @click-left="handleBack"
    />

    <!-- 详情内容 -->
    <wd-cell-group border>
      <wd-cell title="分类名称" :value="formData.name || '-'" />
      <wd-cell title="分类排序" :value="formData.sort != null ? String(formData.sort) : '-'" />
      <wd-cell v-if="formData.status === CommonStatusEnum.DISABLE" title="分类状态">
        <wd-tag type="warning" variant="plain">
          停用
        </wd-tag>
      </wd-cell>
      <wd-cell title="创建时间" :value="formatDateTime(formData.createTime) || '-'" />
    </wd-cell-group>

    <!-- 底部操作按钮 -->
    <view v-if="hasAccessByCodes(['product:category:update', 'product:category:delete'])" class="yd-detail-footer">
      <view class="yd-detail-footer-actions">
        <wd-button v-if="hasAccessByCodes(['product:category:update'])" class="flex-1" type="warning" @click="handleEdit">
          编辑
        </wd-button>
        <wd-button v-if="hasAccessByCodes(['product:category:delete'])" class="flex-1" type="danger" :loading="deleting" @click="handleDelete">
          删除
        </wd-button>
      </view>
    </view>
  </view>
</template>

<script lang="ts" setup>
import type { ProductCategory } from '@/api/mall/product/category'
import { onUnload } from '@dcloudio/uni-app'
import { useDialog } from '@wot-ui/ui/components/wd-dialog'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { onMounted, ref } from 'vue'
import { deleteProductCategory, getProductCategory } from '@/api/mall/product/category'
import { useAccess } from '@/hooks/useAccess'
import { delay, navigateBackPlus } from '@/utils'
import { CommonStatusEnum } from '@/utils/constants'
import { formatDateTime } from '@/utils/date'

const props = defineProps<{ id?: number | any }>()

definePage({
  style: {
    navigationBarTitleText: '',
    navigationStyle: 'custom',
  },
})

const { hasAccessByCodes } = useAccess()
const dialog = useDialog()
const toast = useToast()
const formData = ref<ProductCategory>({} as ProductCategory) // 详情数据
const deleting = ref(false) // 删除状态
/** 返回上一页 */
function handleBack() {
  navigateBackPlus('/pages-mall/product/category/index')
}

/** 加载详情 */
async function getDetail() {
  if (!props.id) {
    return
  }
  try {
    toast.loading('加载中...')
    formData.value = await getProductCategory(props.id)
  } finally {
    toast.close()
  }
}

/** 编辑 */
function handleEdit() {
  uni.navigateTo({ url: `/pages-mall/product/category/form/index?id=${props.id}` })
}

/** 删除 */
async function handleDelete() {
  if (!props.id) {
    return
  }
  try {
    await dialog.confirm({ title: '提示', msg: '确定要删除该分类吗？' })
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteProductCategory(props.id)
    toast.success('删除成功')
    uni.$emit('mall:product-category:reload')
    delay(handleBack)
  } finally {
    deleting.value = false
  }
}

/** 初始化 */
onMounted(() => {
  getDetail()
  uni.$on('mall:product-category:reload', getDetail)
})

/** 卸载 */
onUnload(() => {
  uni.$off('mall:product-category:reload', getDetail)
})
</script>
