<template>
  <view class="pickup-location">
    <view class="pickup-location__summary">
      {{ hasLocation ? (selectedLabel || '已在地图标记门店位置') : '请在地图上标记实际门店位置' }}
    </view>
    <wd-button size="small" variant="plain" @click="open">
      {{ hasLocation ? '重新选点' : '地图选点' }}
    </wd-button>
  </view>
  <!-- #ifdef H5 -->
  <wd-popup v-model="visible" position="bottom" :duration="0" :z-index="100" custom-style="height: 90vh; border-radius: 16px 16px 0 0;">
    <view class="pickup-location__popup">
      <view class="pickup-location__header">
        <text>选择自提位置</text>
        <wd-button size="small" variant="text" @click="visible = false">
          取消
        </wd-button>
      </view>
      <iframe v-if="visible && pickerHtml" ref="frameRef" :srcdoc="pickerHtml" title="高德地图选点" class="pickup-location__map" />
      <view v-else class="pickup-location__empty">
        地图服务尚未配置，暂时无法选点。可以先保存为待完善。
      </view>
    </view>
  </wd-popup>
  <!-- #endif -->
</template>

<script lang="ts" setup>
import type { MapCoordinate } from '@/utils/coordinate'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
// #ifdef H5
import { AMAP_PICKER_RESULT, buildAMapPickerHtml } from './amap-picker'
// #endif

const props = defineProps<{
  latitude?: number | null
  longitude?: number | null
  address: string
  city: string
}>()
const emit = defineEmits<{ select: [point: MapCoordinate] }>()
const hasLocation = computed(() => Number.isFinite(props.latitude) && Number.isFinite(props.longitude))
const selectedLabel = ref('')
// #ifdef H5
const visible = ref(false)
const pickerHtml = ref('')
const frameRef = ref<HTMLIFrameElement>()

function handleMessage(event: MessageEvent) {
  if (!visible.value || !frameRef.value || event.source !== frameRef.value.contentWindow
    || event.origin !== window.location.origin || event.data?.type !== AMAP_PICKER_RESULT) {
    return
  }
  const { latitude, longitude, label } = event.data
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)
    || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    return
  }
  selectedLabel.value = typeof label === 'string' ? label.slice(0, 300) : ''
  emit('select', { latitude, longitude })
  visible.value = false
}
onMounted(() => window.addEventListener('message', handleMessage))
onBeforeUnmount(() => window.removeEventListener('message', handleMessage))
// #endif

function open() {
  // #ifdef H5
  pickerHtml.value = buildAMapPickerHtml({
    address: props.address,
    city: props.city,
    point: hasLocation.value ? { latitude: props.latitude!, longitude: props.longitude! } : undefined,
  })
  visible.value = true
  // #endif
  // #ifndef H5
  uni.chooseLocation({
    ...(hasLocation.value ? { latitude: props.latitude!, longitude: props.longitude! } : {}),
    success: ({ latitude, longitude, name, address }) => {
      selectedLabel.value = name || address
      emit('select', { latitude, longitude })
    },
    fail: ({ errMsg }) => {
      if (!errMsg.includes('cancel')) {
        uni.showToast({ title: '地图选点未完成，请检查定位权限后重试', icon: 'none' })
      }
    },
  })
  // #endif
}
</script>

<style scoped>
.pickup-location {
  padding: 8rpx 0;
}
.pickup-location__summary {
  margin-bottom: 16rpx;
  color: #778174;
  font-size: 26rpx;
}
.pickup-location__popup {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.pickup-location__header {
  padding: 20rpx 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.pickup-location__map {
  display: block;
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 0;
}
.pickup-location__empty {
  padding: 48rpx;
  color: #778174;
}
</style>
