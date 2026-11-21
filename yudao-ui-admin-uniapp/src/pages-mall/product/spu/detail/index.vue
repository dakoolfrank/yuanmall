<template>
  <view class="yd-page-container product-detail">
    <wd-navbar title="商品详情" left-arrow placeholder safe-area-inset-top fixed @click-left="handleBack" />
    <scroll-view class="min-h-0 flex-1" scroll-y>
      <view class="detail-content">
        <view v-if="loading" class="detail-message">
          正在加载商品…
        </view>
        <view v-else-if="loadFailed" class="detail-message">
          <text>商品加载失败</text><button class="text-button" @click="getDetail">
            重新加载
          </button>
        </view>
        <template v-else-if="formData.id">
          <view class="detail-card overview">
            <view class="cover">
              <image v-if="formData.picUrl && !imageFailed" :key="imageAttempt" :src="formData.picUrl" mode="aspectFit" class="cover-image" @error="imageFailed = true" @click="preview(formData.picUrl)" />
              <view v-else class="image-placeholder">
                <text>{{ formData.picUrl ? '图片加载失败' : '暂无商品图片' }}</text>
                <button v-if="formData.picUrl" class="text-button" @click="retryImage">
                  重试
                </button>
              </view>
            </view>
            <view class="overview-info">
              <text class="product-name">{{ formData.name || '未命名商品' }}</text>
              <text class="product-price">{{ formatDisplayMoney(formData.price) }}</text>
              <text class="product-state">{{ statusLabel }} · {{ stockLabel }}</text>
              <text v-if="visibleIntroduction" class="product-intro">{{ visibleIntroduction }}</text>
            </view>
          </view>

          <view v-if="formData.specType" class="detail-card">
            <text class="section-title">规格与库存</text>
            <template v-if="formData.specType">
              <view v-for="(sku, index) in formData.skus" :key="sku.id || index" class="sku-row">
                <text class="sku-name">{{ sku.properties?.map(item => item.valueName).join(' / ') || sku.name || '默认规格' }}</text>
                <view class="sku-values">
                  <text>{{ formatDisplayMoney(sku.price) }}</text><text>库存 {{ sku.stock ?? '未知' }}</text>
                </view>
              </view>
              <text v-if="!formData.skus?.length" class="muted">暂无规格信息</text>
            </template>
          </view>

          <view v-if="gallery.length || visibleDescription" class="detail-card">
            <text class="section-title">商品介绍</text>
            <view v-if="gallery.length" class="gallery">
              <image v-for="pic in gallery" :key="pic" :src="pic" mode="aspectFit" class="gallery-image" @click="preview(pic)" />
            </view>
            <rich-text v-if="visibleDescription" class="product-description" :nodes="visibleDescription" />
          </view>

          <view class="detail-card">
            <button class="more-information" @click="toggleInformation">
              <text>更多信息</text><text>{{ informationOpen ? '收起 −' : '展开 +' }}</text>
            </button>
            <view v-if="informationOpen" class="information">
              <view v-if="optionsFailed" class="muted">
                名称信息加载失败 <text class="retry-options" @click="loadOptions">重试</text>
              </view>
              <view class="info-row">
                <text class="info-label">分类</text><text class="info-value">{{ categoryName || formData.categoryId || '未设置' }}</text>
              </view>
              <view class="info-row">
                <text class="info-label">配送方式</text><view class="info-value"><dict-tag v-for="type in formData.deliveryTypes" :key="type" :type="DICT_TYPE.TRADE_DELIVERY_TYPE" :value="type" /></view>
              </view>
              <view v-if="formData.deliveryTypes?.includes(DeliveryTypeEnum.EXPRESS)" class="info-row">
                <text class="info-label">运费模板</text><text class="info-value">{{ templateName || formData.deliveryTemplateId || '未设置' }}</text>
              </view>
            </view>
          </view>
        </template>
      </view>
    </scroll-view>
    <view v-if="formData.id && !loadFailed && canUpdate" class="detail-footer">
      <view class="detail-footer__inner">
        <button class="action-primary" :disabled="busy" @click="handleEdit">
          编辑
        </button>
        <button class="action-secondary" :disabled="busy || saleAction.status == null" :loading="statusChanging && pendingStatus === saleAction.status" @click="handleStatusChange(saleAction.status)">
          {{ saleAction.label }}
        </button>
        <button class="action-recycle" :disabled="busy || formData.status === ProductSpuStatusEnum.RECYCLE" :loading="statusChanging && pendingStatus === ProductSpuStatusEnum.RECYCLE" @click="handleStatusChange(ProductSpuStatusEnum.RECYCLE)">
          {{ formData.status === ProductSpuStatusEnum.RECYCLE ? '已回收' : '回收' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script lang="ts" setup>
import { parseId } from '@/utils/id'

import type { ProductSpu } from '@/api/mall/product/spu'
import { onUnload } from '@dcloudio/uni-app'
import { useDialog } from '@wot-ui/ui/components/wd-dialog'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, onMounted, ref } from 'vue'
import { getProductCategoryList } from '@/api/mall/product/category'
import { getProductSpu, updateProductSpuStatus } from '@/api/mall/product/spu'
import { getSimpleDeliveryExpressTemplateList } from '@/api/mall/trade/delivery/express-template'
import { useAccess } from '@/hooks/useAccess'
import { formatDisplayMoney } from '@/utils/format'
import { navigateBackPlus } from '@/utils'
import { DeliveryTypeEnum, DICT_TYPE, ProductSpuStatusEnum } from '@/utils/constants'

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
const loading = ref(true)
const loadFailed = ref(false)
const informationOpen = ref(false)
const optionsLoaded = ref(false)
const optionsFailed = ref(false)
const imageFailed = ref(false)
const imageAttempt = ref(0)
const formData = ref<ProductSpu>({}) // 详情数据
const statusChanging = ref(false) // 回收/恢复切换中
const categoryNameMap = ref<Record<number, string>>({}) // 分类 id→名称（详情仅返回 categoryId，按 id 映射）
const templateNameMap = ref<Record<number, string>>({}) // 运费模板 id→名称
const categoryName = computed(() => formData.value.categoryId != null ? categoryNameMap.value[formData.value.categoryId] : '')
const templateName = computed(() => formData.value.deliveryTemplateId != null ? templateNameMap.value[formData.value.deliveryTemplateId] : '')

const pendingStatus = ref<number | null>(null)
const canUpdate = computed(() => hasAccessByCodes(['product:spu:update']))
const saleAction = computed(() => {
  if (formData.value.status === ProductSpuStatusEnum.ENABLE)
    return { label: '下架', status: ProductSpuStatusEnum.DISABLE }
  if (formData.value.status === ProductSpuStatusEnum.DISABLE)
    return { label: '上架', status: ProductSpuStatusEnum.ENABLE }
  if (formData.value.status === ProductSpuStatusEnum.RECYCLE)
    return { label: '恢复', status: ProductSpuStatusEnum.DISABLE }
  return { label: '上下架', status: null }
})
const busy = computed(() => loading.value || statusChanging.value)
const statusLabel = computed(() => formData.value.status === ProductSpuStatusEnum.ENABLE ? '已上架' : formData.value.status === ProductSpuStatusEnum.DISABLE ? '已下架' : formData.value.status === ProductSpuStatusEnum.RECYCLE ? '回收站' : '状态未知')
const stockLabel = computed(() => {
  const stock = formData.value.stock
  return stock == null ? '库存未知' : stock === 0 ? '缺货' : `剩余 ${stock} 件${stock <= 10 ? ' · 库存不足' : ''}`
})
const gallery = computed(() => Array.from(new Set(formData.value.sliderPicUrls || [])).filter(pic => pic !== formData.value.picUrl))
// 只判断展示是否重复；保留原始字段及含图片等内容的富文本。
function normalizeText(value?: string) {
  return (value || '').replace(/&nbsp;|&#160;|&#xA0;/gi, ' ').replace(/\s+/g, ' ').trim()
}
const visibleIntroduction = computed(() => normalizeText(formData.value.introduction) === normalizeText(formData.value.name) ? '' : formData.value.introduction || '')
const visibleDescription = computed(() => {
  const raw = formData.value.description || ''
  // 只对常见文本格式标签去重，其他富文本内容原样展示。
  const text = normalizeText(raw.replace(/<\/?(?:p|div|span|br|strong|[bi]|em)\b[^>]*>/gi, ''))
  if (!text || text === normalizeText(formData.value.name) || text === normalizeText(formData.value.introduction))
    return ''
  return raw
})
function retryImage() {
  imageAttempt.value++
  imageFailed.value = false
}
function preview(pic: string) {
  uni.previewImage({ current: pic, urls: Array.from(new Set([formData.value.picUrl, ...gallery.value].filter(Boolean))) as string[] })
}
function toggleInformation() {
  informationOpen.value = !informationOpen.value
  if (informationOpen.value && !optionsLoaded.value)
    void loadOptions()
}
/** 返回上一页 */
function handleBack() {
  navigateBackPlus('/pages-mall/product/spu/index')
}

/** 加载分类/运费模板名称映射 */
async function loadOptions() {
  optionsFailed.value = false
  try {
    const [categories, templates] = await Promise.all([
      getProductCategoryList({}),
      getSimpleDeliveryExpressTemplateList(),
    ])
    categoryNameMap.value = Object.fromEntries(categories.map(item => [parseId(item.id) as number, item.name || String(item.id)]))
    templateNameMap.value = Object.fromEntries(templates.map(item => [parseId(item.id) as number, item.name || String(item.id)]))
    optionsLoaded.value = true
  } catch {
    optionsFailed.value = true
  }
}

/** 加载详情 */
async function getDetail() {
  loading.value = true
  loadFailed.value = false
  try {
    const id = parseId(props.id)
    if (!id)
      throw new Error('Invalid product id')
    formData.value = await getProductSpu(id)
    if (!formData.value?.id)
      throw new Error('Product not found')
    imageFailed.value = false
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** 编辑 */
function handleEdit() {
  if (!canUpdate.value || busy.value || !formData.value.id)
    return
  uni.navigateTo({ url: `/pages-mall/product/spu/form/index?id=${formData.value.id}` })
}

/** 上下架、回收、恢复均复用状态接口；回收不删除商品。 */
async function handleStatusChange(status: number | null) {
  const id = formData.value.id
  const previousStatus = formData.value.status
  if (!id || !canUpdate.value || busy.value || status == null) {
    return
  }
  if (status !== saleAction.value.status
    && !(status === ProductSpuStatusEnum.RECYCLE && previousStatus != null && [ProductSpuStatusEnum.ENABLE, ProductSpuStatusEnum.DISABLE].includes(previousStatus))) {
    return
  }
  const text = status === ProductSpuStatusEnum.RECYCLE
    ? '回收'
    : previousStatus === ProductSpuStatusEnum.RECYCLE
      ? '恢复'
      : status === ProductSpuStatusEnum.ENABLE ? '上架' : '下架'
  statusChanging.value = true
  pendingStatus.value = status
  try {
    try {
      await dialog.confirm({ title: `${text}商品`, msg: status === ProductSpuStatusEnum.RECYCLE
        ? '将商品移入回收站？之后可以恢复。'
        : `确定${text}该商品吗？` })
    } catch {
      return
    }
    if (formData.value.id !== id || formData.value.status !== previousStatus || !canUpdate.value) {
      toast.warning('商品状态已变化，请刷新后重试')
      return
    }
    await updateProductSpuStatus({ id, status })
    toast.success(`${text}成功`)
    uni.$emit('mall:product-spu:reload')
  } finally {
    statusChanging.value = false
    pendingStatus.value = null
  }
}

/** 初始化 */
onMounted(() => {
  getDetail()
  uni.$on('mall:product-spu:reload', getDetail)
})

/** 卸载 */
onUnload(() => {
  uni.$off('mall:product-spu:reload', getDetail)
})
</script>

<style scoped>
.product-detail {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f4f6f2;
}
.detail-content {
  max-width: 880px;
  width: 100%;
  margin: 0 auto;
  padding: 20px 16px 32px;
  box-sizing: border-box;
}
.detail-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 14px;
}
.overview {
  display: flex;
  gap: 24px;
}
.cover {
  width: 180px;
  height: 180px;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f6f3;
}
.cover-image {
  width: 100%;
  height: 100%;
}
.image-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #879087;
  font-size: 13px;
}
.overview-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.product-name {
  font-size: 23px;
  font-weight: 600;
  color: #263c2e;
  overflow-wrap: anywhere;
}
.product-price {
  font-size: 24px;
  color: #a95b32;
}
.product-state,
.product-intro {
  font-size: 14px;
  color: #66776b;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.section-title {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #263c2e;
  margin-bottom: 16px;
}
.muted {
  color: #879087;
  font-size: 14px;
}
.sku-row {
  padding: 12px 0;
  border-top: 1px solid #edf0eb;
}
.sku-name {
  font-size: 15px;
}
.sku-values {
  display: flex;
  gap: 24px;
  margin-top: 8px;
  color: #66776b;
  font-size: 14px;
}
.gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.gallery-image {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  background: #f5f6f3;
}
.product-description {
  display: block;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  overflow: hidden;
}
/* #ifdef H5 */
.product-description :deep(img) {
  max-width: 100%;
  height: auto;
}
/* #endif */
.more-information {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0;
  margin: 0;
  background: white;
  font-size: 15px;
  color: #46634e;
  line-height: 1.6;
  text-align: left;
}
.more-information::after,
.text-button::after,
.detail-footer button::after {
  border: none;
}
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  padding: 12px 0;
  border-bottom: 1px solid #edf0eb;
  color: #66776b;
  font-size: 14px;
  line-height: 1.6;
}
.info-value {
  text-align: right;
  overflow-wrap: anywhere;
  min-width: 0;
}
.info-label {
  flex-shrink: 0;
}
.information {
  margin-top: 16px;
}
.detail-footer {
  flex-shrink: 0;
  background: white;
  border-top: 1px solid #e7ece3;
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
}
.detail-footer__inner {
  display: flex;
  gap: 12px;
  max-width: 848px;
  margin: 0 auto;
}
.detail-footer button {
  margin: 0;
  font-size: 15px;
  line-height: 44px;
  border-radius: 8px;
}
.action-primary {
  flex: 1;
  background: #2e644b;
  color: white;
}
.action-secondary {
  flex: 1;
  background: #edf2e9;
  color: #2e644b;
}
.action-recycle {
  flex: 1;
  background: #faf0eb;
  color: #a55035;
  padding: 0 8px;
}
.text-button {
  background: transparent;
  color: #2e644b;
  font-size: 13px;
  padding: 0 12px;
  margin: 4px 0 0;
}
.detail-message {
  padding: 40px 0;
  text-align: center;
  color: #66776b;
}
.retry-options {
  color: #2e644b;
}
@media (max-width: 480px) {
  .detail-content {
    padding: 12px;
  }
  .detail-card {
    padding: 16px;
  }
  .overview {
    gap: 14px;
  }
  .cover {
    width: 104px;
    height: 104px;
  }
  .product-name {
    font-size: 18px;
  }
  .product-price {
    font-size: 21px;
  }
  .overview-info {
    gap: 6px;
  }
}
</style>
