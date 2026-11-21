<template>
  <view class="yd-page-container">
    <!-- 顶部导航栏 -->
    <wd-navbar
      :title="getTitle"
      left-arrow placeholder safe-area-inset-top fixed
      @click-left="handleBack"
    />

    <!-- 表单区域 -->
    <view>
      <view class="p-24rpx text-26rpx text-[#778174]">
        信息未填齐可先保存，补齐后再启用。
      </view>
      <wd-form ref="formRef" :model="formData" :schema="formSchema">
        <wd-cell-group border>
          <wd-form-item title="门店名称" title-width="200rpx" prop="name">
            <wd-input v-model="formData.name" clearable placeholder="请输入门店名称" />
          </wd-form-item>
          <wd-form-item title="联系电话" title-width="200rpx" prop="phone">
            <wd-input v-model="formData.phone" clearable placeholder="请输入联系电话" />
          </wd-form-item>
          <wd-form-item title="门店 Logo" title-width="200rpx" prop="logo">
            <yd-upload-img v-model="formData.logo" />
          </wd-form-item>
          <yd-tree-select v-model="formData.areaId" label="所在地区" prop="areaId" label-width="200rpx" :data="areaTree" placeholder="请选择所在地区" />
          <wd-form-item title="详细地址" title-width="200rpx" prop="detailAddress">
            <wd-textarea v-model="formData.detailAddress" clearable :maxlength="200" placeholder="请输入详细地址" />
          </wd-form-item>
          <wd-form-item title="营业开始" title-width="200rpx" prop="openingTime" is-link :value="formData.openingTime" placeholder="请选择营业开始时间" @click="timePickerVisible.opening = true" />
          <wd-datetime-picker v-model="formData.openingTime" v-model:visible="timePickerVisible.opening" title="请选择营业开始时间" type="time" />
          <wd-form-item title="营业结束" title-width="200rpx" prop="closingTime" is-link :value="formData.closingTime" placeholder="请选择营业结束时间" @click="timePickerVisible.closing = true" />
          <wd-datetime-picker v-model="formData.closingTime" v-model:visible="timePickerVisible.closing" title="请选择营业结束时间" type="time" />
          <wd-form-item title="地图位置" title-width="200rpx" prop="latitude">
            <LocationPicker
              :latitude="formData.latitude" :longitude="formData.longitude"
              :address="locationAddress" :city="formData.areaId ? String(formData.areaId) : ''"
              @select="handleLocationSelect"
            />
          </wd-form-item>
          <wd-form-item title="门店状态" title-width="200rpx" prop="status" center>
            <wd-radio-group v-model="formData.status" type="button">
              <wd-radio :value="CommonStatusEnum.DISABLE">
                停用 / 待完善
              </wd-radio>
              <wd-radio :value="CommonStatusEnum.ENABLE">
                启用
              </wd-radio>
            </wd-radio-group>
          </wd-form-item>
          <wd-form-item title="简介" title-width="200rpx" prop="introduction">
            <wd-textarea v-model="formData.introduction" clearable :maxlength="500" placeholder="请输入简介" />
          </wd-form-item>
        </wd-cell-group>
      </wd-form>
    </view>

    <!-- 底部保存按钮 -->
    <view class="yd-detail-footer">
      <wd-button
        type="primary"
        block
        :loading="formLoading"
        @click="handleSubmit"
      >
        保存
      </wd-button>
    </view>
  </view>
</template>

<script lang="ts" setup>
import { parseId } from '@/utils/id'

import type { FormInstance } from '@wot-ui/ui/components/wd-form/types'
import type { DeliveryPickUpStore } from '@/api/mall/trade/delivery/pick-up-store'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createDeliveryPickUpStore,
  getDeliveryPickUpStore,
  updateDeliveryPickUpStore,
} from '@/api/mall/trade/delivery/pick-up-store'
import { getAreaTree } from '@/api/system/area'
import { delay, navigateBackPlus } from '@/utils'
import { CommonStatusEnum } from '@/utils/constants'
import { createFormSchema } from '@/utils/wot'
import { isMobile } from '@/utils/validator'
import type { MapCoordinate } from '@/utils/coordinate'
import LocationPicker from '../components/location-picker.vue'

const props = defineProps<{
  id?: number | any
}>()

definePage({
  style: {
    navigationBarTitleText: '',
    navigationStyle: 'custom',
  },
})

const toast = useToast()
const getTitle = computed(() => props.id ? '编辑自提门店' : '新增自提门店')
const formLoading = ref(false) // 表单提交状态
const areaTree = ref<any[]>([]) // 地区树数据
const formData = ref<DeliveryPickUpStore>({
  id: undefined,
  name: '团团园园杂货铺自提点',
  phone: '',
  logo: 'https://mall.nexrur.ai/static/brand/yuanyuan-logo.png',
  areaId: undefined,
  detailAddress: '',
  openingTime: '',
  closingTime: '',
  latitude: null,
  longitude: null,
  status: CommonStatusEnum.DISABLE,
  introduction: '',
}) // 表单数据
const formSchema = createFormSchema({
  name: [{ required: true, message: '门店名称不能为空' }],
  phone: [
    { required: () => formData.value.status === CommonStatusEnum.ENABLE, message: '启用前请填写联系电话' },
    { validator: value => !value || isMobile(String(value)) || '手机号格式不正确' },
  ],
  logo: [{ required: true, message: '门店 Logo 不能为空' }],
  areaId: [{ required: true, message: '所在地区不能为空' }],
  detailAddress: [{ required: true, message: '详细地址不能为空' }],
  openingTime: [{ required: () => formData.value.status === CommonStatusEnum.ENABLE, message: '启用前请填写营业开始时间' }],
  closingTime: [{ required: () => formData.value.status === CommonStatusEnum.ENABLE, message: '启用前请填写营业结束时间' }],
  status: [{ required: true, message: '开启状态不能为空' }],
  latitude: [{ required: () => formData.value.status === CommonStatusEnum.ENABLE, message: '启用前请在地图选择门店位置', type: 'number', min: -90, max: 90 }],
  longitude: [{ required: () => formData.value.status === CommonStatusEnum.ENABLE, message: '启用前请在地图选择门店位置', type: 'number', min: -180, max: 180 }],
})
const formRef = ref<FormInstance>() // 表单组件引用
const timePickerVisible = reactive({ opening: false, closing: false }) // 营业时间选择弹窗
const locationAddress = computed(() => `${formData.value.areaName || ''} ${formData.value.detailAddress || ''}`.trim())

function handleLocationSelect(point: MapCoordinate) {
  formData.value.latitude = point.latitude
  formData.value.longitude = point.longitude
}

/** 返回上一页 */
function handleBack() {
  navigateBackPlus('/pages-mall/trade/delivery/pick-up-store/index')
}

/** 加载详情 */
async function getDetail() {
  if (!props.id) {
    return
  }
  const data = await getDeliveryPickUpStore(parseId(props.id) as number)
  formData.value = { ...data, openingTime: data.openingTime || '', closingTime: data.closingTime || '' }
}

/** 提交表单 */
async function handleSubmit() {
  const { valid } = await formRef.value.validate()
  if (!valid) {
    return
  }

  formLoading.value = true
  try {
    const data: DeliveryPickUpStore = {
      ...formData.value,
      phone: formData.value.phone?.trim() || '',
      openingTime: formData.value.openingTime || null,
      closingTime: formData.value.closingTime || null,
      latitude: nullableCoordinate(formData.value.latitude),
      longitude: nullableCoordinate(formData.value.longitude),
    }
    if (props.id) {
      await updateDeliveryPickUpStore(data)
      toast.success('修改成功')
    } else {
      await createDeliveryPickUpStore(data)
      toast.success('新增成功')
    }
    uni.$emit('mall:delivery-pick-up-store:reload')
    delay(handleBack)
  } finally {
    formLoading.value = false
  }
}

function nullableCoordinate(value: unknown): number | null {
  return value == null || value === '' ? null : Number(value)
}

/** 初始化 */
onMounted(async () => {
  await Promise.all([
    getDetail(),
    getAreaTree().then((list) => {
      areaTree.value = list
    }),
  ])
})
</script>
