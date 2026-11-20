<template>
  <view class="yd-page-container">
    <!-- 顶部导航栏 -->
    <wd-navbar
      title="自提门店详情"
      left-arrow placeholder safe-area-inset-top fixed
      @click-left="handleBack"
    />

    <!-- 详情内容 -->
    <wd-cell-group border>
      <wd-cell title="门店名称" :value="formData.name || '-'" />
      <wd-cell title="联系电话" :value="formData.phone || '待补充'" />
      <wd-cell title="门店 Logo">
        <wd-img v-if="formData.logo" :src="formData.logo" width="112rpx" height="112rpx" radius="8rpx" mode="aspectFill" enable-preview />
        <text v-else>-</text>
      </wd-cell>
      <wd-cell title="所在地区" :value="formData.areaName || '-'" />
      <wd-cell title="详细地址" :value="formData.detailAddress || '-'" />
      <wd-cell title="营业开始" :value="formData.openingTime || '待补充'" />
      <wd-cell title="营业结束" :value="formData.closingTime || '待补充'" />
      <wd-cell title="地图位置" :value="Number.isFinite(formData.latitude) && Number.isFinite(formData.longitude) ? '已标记' : '待选点'" />
      <wd-cell title="取货负责人" :value="pickupStaffName" />
      <wd-cell title="开启状态">
        <wd-tag v-if="formData.status === 1 && isPickUpStoreIncomplete(formData)" type="warning" variant="plain">
          待完善
        </wd-tag>
        <dict-tag v-else-if="formData.status != null" :type="DICT_TYPE.COMMON_STATUS" :value="formData.status" />
        <text v-else>-</text>
      </wd-cell>
      <wd-cell title="简介" :value="formData.introduction || '-'" />
      <wd-cell title="创建时间" :value="formatBeijingDateTime(formData.createTime) || '-'" />
    </wd-cell-group>

    <!-- 底部操作按钮 -->
    <view v-if="hasAccessByCodes(['trade:delivery:pick-up-store:update', 'trade:delivery:pick-up-store:delete'])" class="yd-detail-footer">
      <view class="yd-detail-footer-actions">
        <wd-button v-if="hasAccessByCodes(['trade:delivery:pick-up-store:update'])" class="flex-1" type="warning" @click="handleEdit">
          编辑
        </wd-button>
        <wd-button v-if="hasAccessByCodes(['trade:delivery:pick-up-store:delete'])" class="flex-1" type="danger" :loading="deleting" @click="handleDelete">
          删除
        </wd-button>
      </view>
    </view>
  </view>
</template>

<script lang="ts" setup>
import { parseId } from '@/utils/id'

import type { DeliveryPickUpStore } from '@/api/mall/trade/delivery/pick-up-store'
import { onUnload } from '@dcloudio/uni-app'
import { useDialog } from '@wot-ui/ui/components/wd-dialog'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, onMounted, ref } from 'vue'
import {
  deleteDeliveryPickUpStore,
  getDeliveryPickUpStore,
} from '@/api/mall/trade/delivery/pick-up-store'
import { useAccess } from '@/hooks/useAccess'
import { useUserStore } from '@/store/user'
import { delay, navigateBackPlus } from '@/utils'
import { DICT_TYPE } from '@/utils/constants'
import { formatBeijingDateTime } from '@/utils/date'
import { isPickUpStoreIncomplete } from '@/utils/pickUpStore'

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
const userStore = useUserStore()
const formData = ref<DeliveryPickUpStore>({} as DeliveryPickUpStore) // 详情数据
const deleting = ref(false) // 删除状态
const pickupStaffName = computed(() => {
  const currentUser = userStore.userInfo
  const currentUserId = currentUser.id ?? currentUser.userId
  return (formData.value.verifyUsers || []).map((user) => {
    // 已绑定的负责人就是当前账号时显示账号名，避免店主看到框架演示昵称。
    return user.id != null && String(user.id) === String(currentUserId)
      ? currentUser.username || user.nickname || '已设置'
      : user.nickname || '已设置'
  }).join('、') || '未设置'
})

/** 返回上一页 */
function handleBack() {
  navigateBackPlus('/pages-mall/trade/delivery/pick-up-store/index')
}

/** 加载详情 */
async function getDetail() {
  if (!props.id) {
    return
  }
  try {
    toast.loading('加载中...')
    formData.value = await getDeliveryPickUpStore(parseId(props.id) as number)
  } finally {
    toast.close()
  }
}

/** 编辑 */
function handleEdit() {
  uni.navigateTo({ url: `/pages-mall/trade/delivery/pick-up-store/form/index?id=${props.id}` })
}

/** 删除 */
async function handleDelete() {
  if (!props.id) {
    return
  }
  try {
    await dialog.confirm({ title: '提示', msg: '确定要删除该自提门店吗？' })
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteDeliveryPickUpStore(parseId(props.id) as number)
    toast.success('删除成功')
    uni.$emit('mall:delivery-pick-up-store:reload')
    delay(handleBack)
  } finally {
    deleting.value = false
  }
}

/** 初始化 */
onMounted(() => {
  getDetail()
  uni.$on('mall:delivery-pick-up-store:reload', getDetail)
})

/** 卸载 */
onUnload(() => {
  uni.$off('mall:delivery-pick-up-store:reload', getDetail)
})
</script>
