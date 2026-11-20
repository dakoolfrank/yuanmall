<template>
  <view v-if="hasAccessByCodes(['trade:config:query'])" class="store-delivery-settings mx-24rpx my-16rpx rounded-16rpx bg-white p-24rpx">
    <view class="mb-16rpx text-32rpx font-semibold">
      店铺配送范围
    </view>
    <view v-if="loading" class="text-26rpx text-gray-500">
      正在加载…
    </view>
    <view v-else-if="failed">
      <text class="text-26rpx text-gray-500">配送范围加载失败</text>
      <wd-button size="small" plain @click="load">
        重试
      </wd-button>
    </view>
    <template v-else>
      <view class="mb-12rpx text-26rpx text-gray-500">
        配送中心
      </view>
      <wd-button plain block :disabled="!canSave || saving" @click="centerVisible = true">
        {{ centerName }}
      </wd-button>
      <wd-picker
        v-model="centerSelection" v-model:visible="centerVisible" title="选择配送中心"
        :columns="stores" value-key="id" label-key="name"
      />
      <wd-input v-model="radiusKm" label="直线半径（公里）" type="digit" :disabled="!canSave || saving" />
      <view class="my-16rpx text-26rpx text-[#778174]">
        范围内免费配送，超出只能自提。按收货地址的地图位置判断。
      </view>
      <view v-if="!config?.enabled" class="mb-16rpx text-26rpx text-orange-600">
        当前配送中心未就绪，请选择已启用且已标记位置的自提点。
      </view>
      <wd-button v-if="canSave" size="small" :loading="saving" :disabled="saving" @click="save">
        保存配送范围
      </wd-button>
    </template>
  </view>
</template>

<script lang="ts" setup>
import type { StoreDeliveryRangeConfig } from '@/api/mall/trade/config'
import type { DeliveryPickUpStore } from '@/api/mall/trade/delivery/pick-up-store'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, onMounted, ref } from 'vue'
import { getStoreDeliveryRange, saveStoreDeliveryRange } from '@/api/mall/trade/config'
import { getSimpleDeliveryPickUpStoreList } from '@/api/mall/trade/delivery/pick-up-store'
import { useAccess } from '@/hooks/useAccess'

const { hasAccessByCodes } = useAccess()
const canSave = computed(() => hasAccessByCodes(['trade:config:save']))
const toast = useToast()
const loading = ref(true)
const saving = ref(false)
const failed = ref(false)
const stores = ref<DeliveryPickUpStore[]>([])
const config = ref<StoreDeliveryRangeConfig>()
const centerId = ref<number>()
const centerVisible = ref(false)
const centerName = computed(() => stores.value.find(item => item.id === centerId.value)?.name
  || (centerId.value === config.value?.pickUpStoreId ? config.value?.storeName : '')
  || '请选择已启用自提点')
const centerSelection = computed({
  get: () => centerId.value ? [centerId.value] : [],
  set: (values: (number | string)[]) => { centerId.value = values[0] == null ? undefined : Number(values[0]) },
})
const radiusKm = ref('5')

async function load() {
  if (!hasAccessByCodes(['trade:config:query']))
    return
  loading.value = true
  failed.value = false
  try {
    const [value, list] = await Promise.all([getStoreDeliveryRange(), getSimpleDeliveryPickUpStoreList()])
    config.value = value
    centerId.value = value.pickUpStoreId
    radiusKm.value = String((value.radiusMeters ?? 5000) / 1000)
    stores.value = list
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function save() {
  if (saving.value || !canSave.value)
    return
  const radiusMeters = Math.round(Number(radiusKm.value) * 1000)
  if (!centerId.value || !Number.isFinite(radiusMeters) || radiusMeters < 1 || radiusMeters > 100000) {
    toast.warning('请选择配送中心，半径需大于0且不超过100公里')
    return
  }
  saving.value = true
  try {
    await saveStoreDeliveryRange({ pickUpStoreId: centerId.value, radiusMeters })
    toast.success('配送范围已保存')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
