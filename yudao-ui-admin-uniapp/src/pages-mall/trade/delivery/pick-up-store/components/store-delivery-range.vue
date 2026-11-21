<template>
  <view v-if="hasAccessByCodes(['trade:config:query'])" class="store-delivery-settings">
    <view class="range-title">
      配送设置
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
      <view class="range-row">
        <text class="range-label">配送中心</text>
        <button class="range-center" :disabled="!canSave || saving" @click="centerVisible = true">
          <text>{{ centerName }}</text>
          <wd-icon name="arrow-down" size="14px" />
        </button>
      </view>
      <wd-picker
        v-model="centerSelection" v-model:visible="centerVisible" title="选择配送中心"
        :columns="stores" value-key="id" label-key="name"
      />
      <view class="range-row range-row--radius">
        <label class="range-label" for="store-delivery-radius">配送半径</label>
        <view class="range-radius">
          <view class="range-distance">
            <input
              id="store-delivery-radius" v-model="radiusKm" class="range-input" type="digit"
              aria-label="配送半径（公里）" :disabled="!canSave || saving"
            >
            <text>公里</text>
          </view>
          <text class="range-note">范围内免费配送，超出可自提</text>
        </view>
      </view>
      <view v-if="canSave && !radiusValid" class="range-warning">
        配送半径须在 0.001 至 100 公里之间。
      </view>
      <view v-if="!config?.enabled" class="mb-16rpx text-26rpx text-orange-600">
        当前配送中心未就绪，请选择已启用且已标记位置的自提点。
      </view>
      <view v-if="canSave" class="range-actions">
        <wd-button
          size="small" :loading="saving" :disabled="!canSubmit"
          :custom-style="canSubmit ? 'background: #2e644b;' : 'background: #e8eee8; color: #526258;'" @click="save"
        >
          保存
        </wd-button>
      </view>
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
const radiusMeters = computed(() => Math.round(Number(radiusKm.value) * 1000))
const radiusValid = computed(() => radiusMeters.value >= 1 && radiusMeters.value <= 100000)
const canSubmit = computed(() => canSave.value && !saving.value && !!centerId.value
  && radiusValid.value
  && (centerId.value !== config.value?.pickUpStoreId || radiusMeters.value !== (config.value?.radiusMeters ?? 5000)))

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
  if (!canSubmit.value || !centerId.value)
    return
  saving.value = true
  try {
    await saveStoreDeliveryRange({ pickUpStoreId: centerId.value, radiusMeters: radiusMeters.value })
    toast.success('配送范围已保存')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.store-delivery-settings {
  padding: 18px 20px;
  border: 1px solid #e3e9e3;
  border-radius: 14px;
  background: #fff;
  color: #26382e;
}

.range-title {
  margin-bottom: 14px;
  font-size: 17px;
  font-weight: 600;
}

.range-row {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  font-size: 14px;
}

.range-label {
  color: #526258;
  white-space: nowrap;
}

.range-center {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  max-width: 360px;
  min-height: 42px;
  margin: 0;
  padding: 8px 12px;
  border: 1px solid #d6e0d8;
  border-radius: 8px;
  background: #fafbf9;
  color: #26382e;
  font-size: 14px;
  line-height: 1.4;
  text-align: left;
}

.range-center::after {
  border: 0;
}

.range-center:disabled {
  color: #526258;
}

.range-radius,
.range-distance {
  display: flex;
  align-items: center;
  gap: 8px;
}

.range-radius {
  flex-wrap: wrap;
  column-gap: 14px;
}

.range-distance {
  flex-shrink: 0;
  white-space: nowrap;
}

.range-input {
  box-sizing: border-box;
  width: 68px;
  height: 40px;
  padding: 0 9px;
  border: 1px solid #d6e0d8;
  border-radius: 8px;
  background: #fafbf9;
  text-align: center;
}

.range-note {
  color: #657368;
  font-size: 13px;
  line-height: 1.5;
}

.range-warning {
  margin-top: 8px;
  color: #a3422e;
  font-size: 13px;
}

.range-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 480px) {
  .store-delivery-settings {
    padding: 14px;
  }

  .range-row {
    grid-template-columns: 60px minmax(0, 1fr);
    gap: 8px;
  }

  .range-row--radius {
    align-items: start;
  }

  .range-row--radius .range-label {
    line-height: 40px;
  }

  .range-radius {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}
</style>
