<template>
  <view class="yd-page-container product-edit">
    <wd-navbar :title="getTitle" left-arrow placeholder safe-area-inset-top fixed @click-left="handleBack" />
    <scroll-view class="min-h-0 flex-1" scroll-y scroll-with-animation :scroll-into-view="errorAnchor">
      <view v-if="detailLoading || loadFailed" class="edit-message">
        <text>{{ detailLoading ? '正在加载商品…' : '商品加载失败' }}</text>
        <button v-if="loadFailed" class="text-button" @click="loadDetail">
          重新加载
        </button>
      </view>
      <wd-form v-else ref="formRef" :model="formData" :schema="formSchema">
        <view class="edit-content">
          <view id="edit-main" class="edit-card">
            <text class="section-title">商品信息</text>
            <wd-cell-group border>
              <wd-form-item title="商品图片" title-width="88px" prop="picUrl">
                <yd-upload-img v-model="formData.picUrl" />
              </wd-form-item>
              <wd-form-item title="名称" title-width="88px" prop="name">
                <wd-input v-model="formData.name" clearable placeholder="请输入商品名称" />
              </wd-form-item>
              <CategorySelect v-model="formData.categoryId" label="类别" label-width="88px" prop="categoryId" />
            </wd-cell-group>
          </view>
          <view id="edit-skus" class="edit-card">
            <text class="section-title">价格与库存</text>
            <wd-form-item title="规格" title-width="88px" prop="specType" center>
              <wd-radio-group v-model="formData.specType" type="button">
                <wd-radio :value="false">
                  单规格
                </wd-radio><wd-radio :value="true">
                  多规格
                </wd-radio>
              </wd-radio-group>
            </wd-form-item>
            <SkuEditor v-model="skus" :spec-type="formData.specType" :sub-commission-type="formData.subCommissionType" :compact="!settingsOpen" />
          </view>
          <view id="edit-description" class="edit-card">
            <text class="section-title">商品介绍</text>
            <wd-form-item title="介绍" title-width="88px" prop="description">
              <wd-textarea :model-value="descriptionText" class="description-input" auto-height clearable :maxlength="10000" :placeholder="descriptionPlaceholder" @update:model-value="updateDescription" />
            </wd-form-item>
          </view>
          <view id="edit-settings" class="edit-card">
            <button class="settings-toggle" @click="settingsOpen = !settingsOpen">
              <text>其他设置</text><text>{{ settingsOpen ? '收起 −' : '展开 +' }}</text>
            </button>
            <view v-show="settingsOpen" class="settings-fields">
              <wd-cell-group border>
                <BrandSelect v-model="formData.brandId" label="品牌" label-width="88px" prop="brandId" />
                <wd-form-item title="关键词" title-width="88px" prop="keyword">
                  <wd-input v-model="formData.keyword" clearable placeholder="请输入关键词" />
                </wd-form-item>
                <wd-form-item title="简介" title-width="88px" prop="introduction">
                  <wd-textarea v-model="formData.introduction" clearable :maxlength="500" placeholder="一句话介绍商品" />
                </wd-form-item>
                <wd-form-item title="附加图片" title-width="88px" prop="sliderPicUrls">
                  <yd-upload-imgs v-model="formData.sliderPicUrls" :limit="9" />
                </wd-form-item>
                <wd-form-item title="配送方式" title-width="88px" prop="deliveryTypes" center>
                  <wd-checkbox-group v-model="formData.deliveryTypes" type="button">
                    <wd-checkbox v-for="dict in getIntDictOptions(DICT_TYPE.TRADE_DELIVERY_TYPE)" :key="dict.value" :name="dict.value">
                      {{ dict.label }}
                    </wd-checkbox>
                  </wd-checkbox-group>
                </wd-form-item>
                <TemplateSelect v-if="formData.deliveryTypes?.includes(DeliveryTypeEnum.EXPRESS)" v-model="formData.deliveryTemplateId" label="运费模板" label-width="88px" prop="deliveryTemplateId" />
                <wd-form-item title="单独分佣" title-width="88px" prop="subCommissionType" center>
                  <wd-radio-group v-model="formData.subCommissionType" type="button">
                    <wd-radio :value="false">
                      否
                    </wd-radio><wd-radio :value="true">
                      是
                    </wd-radio>
                  </wd-radio-group>
                </wd-form-item>
                <wd-form-item title="排序" title-width="88px" prop="sort">
                  <wd-input-number v-model="formData.sort" :min="0" />
                </wd-form-item>
                <wd-form-item title="赠送积分" title-width="88px">
                  <wd-input-number v-model="formData.giveIntegral" :min="0" />
                </wd-form-item>
                <wd-form-item title="虚拟销量" title-width="88px">
                  <wd-input-number v-model="formData.virtualSalesCount" :min="0" />
                </wd-form-item>
              </wd-cell-group>
            </view>
          </view>
        </view>
      </wd-form>
    </scroll-view>
    <view class="edit-footer">
      <button class="save-button" :disabled="formLoading || detailLoading || loadFailed || !canSave" @click="handleSubmit">
        {{ formLoading ? '保存中…' : '保存商品' }}
      </button>
    </view>
  </view>
</template>

<script lang="ts" setup>
import { parseId } from '@/utils/id'

import type { FormInstance } from '@wot-ui/ui/components/wd-form/types'
import type { ProductSku, ProductSpu } from '@/api/mall/product/spu'
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useAccess } from '@/hooks/useAccess'
import { createProductSpu, getProductSpu, updateProductSpu } from '@/api/mall/product/spu'
import { delay, navigateBackPlus } from '@/utils'
import { DeliveryTypeEnum, DICT_TYPE } from '@/utils/constants'
import BrandSelect from '@/pages-mall/product/brand/components/brand-select.vue'
import CategorySelect from '@/pages-mall/product/category/components/category-select.vue'
import SkuEditor from '@/pages-mall/product/spu/components/sku-editor.vue'
import TemplateSelect from '@/pages-mall/trade/delivery/express-template/components/template-select.vue'
import { getIntDictOptions } from '@/hooks/useDict'
import { fenToYuan, yuanToFen } from '@/utils/format'
import { createFormSchema } from '@/utils/wot'
import { descriptionFromText, descriptionToText } from '@/utils/productDescription'

const props = defineProps<{ id?: number | any }>()

definePage({
  style: {
    navigationBarTitleText: '',
    navigationStyle: 'custom',
  },
})

const toast = useToast()
const formRef = ref<FormInstance>() // 表单组件引用
const formLoading = ref(false) // 表单提交状态
const formId = computed(() => props.id != null && props.id !== '' ? parseId(props.id) : undefined) // 商品编号（路由透传）
const settingsOpen = ref(false)
const detailLoading = ref(true)
const loadFailed = ref(false)
const errorAnchor = ref('')
const { hasAccessByCodes } = useAccess()
const canSave = computed(() => hasAccessByCodes([formId.value ? 'product:spu:update' : 'product:spu:create']))
const getTitle = computed(() => `${formId.value ? '编辑' : '新增'}商品`)
const formData = ref<ProductSpu>({
  name: '',
  categoryId: undefined,
  keyword: '',
  picUrl: '',
  sliderPicUrls: [],
  introduction: '',
  deliveryTypes: [DeliveryTypeEnum.EXPRESS],
  deliveryTemplateId: undefined,
  brandId: undefined,
  specType: false,
  subCommissionType: false,
  description: '',
  sort: 0,
  giveIntegral: 0,
  virtualSalesCount: 0,
})
const descriptionText = ref('')
const originalDescription = ref('')
const originalDescriptionText = ref('')
const descriptionPlaceholder = '直接填写文字，每行一项。例如：\n净含量：508g\n生产日期：2026-10-21\n保质期至：2027-07-20'
function updateDescription(value: string) {
  descriptionText.value = value
  // Unedited text keeps original rich content exactly, including formatting and media.
  formData.value.description = value === originalDescriptionText.value
    ? originalDescription.value
    : descriptionFromText(value, originalDescription.value)
}

const skus = ref<ProductSku[]>([]) // SKU 列表（金额单位元）
const originalSkus = ref<ProductSku[]>([])
const formSchema = createFormSchema({
  name: [{ required: true, message: '商品名称不能为空' }],
  categoryId: [{ required: true, message: '商品分类不能为空' }],
  brandId: [{ required: true, message: '商品品牌不能为空' }],
  keyword: [{ required: true, message: '商品关键字不能为空' }],
  introduction: [{ required: true, message: '商品简介不能为空' }],
  picUrl: [{ required: true, message: '商品封面不能为空' }],
  sliderPicUrls: [{ required: true, message: '轮播图不能为空' }],
  deliveryTypes: [{ required: true, message: '配送方式不能为空' }],
  deliveryTemplateId: [{ required: (model: Record<string, any>) => !!model?.deliveryTypes?.includes(DeliveryTypeEnum.EXPRESS), message: '运费模板不能为空' }],
  specType: [{ required: true, message: '多规格不能为空' }],
  subCommissionType: [{ required: true, message: '单独分佣不能为空' }],
  description: [{ required: true, message: '商品详情不能为空' }],
  sort: [{ required: true, message: '排序不能为空' }],
})
const BASIC_FIELDS = ['categoryId', 'name', 'picUrl', 'description', 'specType']
async function revealError(prop?: string) {
  errorAnchor.value = ''
  if (prop && !BASIC_FIELDS.includes(prop))
    settingsOpen.value = true
  await nextTick()
  errorAnchor.value = prop === 'description' ? 'edit-description' : prop === 'specType' ? 'edit-skus' : prop && BASIC_FIELDS.includes(prop) ? 'edit-main' : 'edit-settings'
}

/** 返回上一页 */
function handleBack() {
  navigateBackPlus('/pages-mall/product/spu/index')
}

/** SKU 分→元 */
function toYuanSku(sku: ProductSku): ProductSku {
  return {
    ...sku,
    price: fenToYuan(sku.price),
    marketPrice: fenToYuan(sku.marketPrice),
    costPrice: fenToYuan(sku.costPrice),
    firstBrokeragePrice: fenToYuan(sku.firstBrokeragePrice),
    secondBrokeragePrice: fenToYuan(sku.secondBrokeragePrice),
  }
}

/** SKU 元→分 */
function toCentSku(sku: ProductSku): ProductSku {
  const original = originalSkus.value.find(item => item.id != null && item.id === sku.id)
  return {
    ...sku,
    name: sku.name || formData.value.name, // SKU 名称取商品名称
    picUrl: sku.picUrl || formData.value.picUrl || '', // SKU 图片默认取商品封面
    price: yuanToFen(sku.price),
    marketPrice: yuanToFen(sku.marketPrice),
    costPrice: original && (original.costPrice == null || Number(original.costPrice) < 0) && Number(sku.costPrice) === 0 ? undefined : yuanToFen(sku.costPrice),
    weight: original && original.weight == null && Number(sku.weight) === 0 ? original.weight : sku.weight,
    volume: original && original.volume == null && Number(sku.volume) === 0 ? original.volume : sku.volume,
    firstBrokeragePrice: yuanToFen(sku.firstBrokeragePrice),
    secondBrokeragePrice: yuanToFen(sku.secondBrokeragePrice),
  }
}

/** 默认单规格 SKU（元） */
function createDefaultSku(): ProductSku {
  return { price: 0, marketPrice: 0, costPrice: 0, stock: 0, barCode: '', weight: 0, volume: 0, picUrl: '', properties: [] }
}

/** 加载详情 */
async function loadDetail() {
  detailLoading.value = true
  loadFailed.value = false
  try {
    if (!formId.value) {
      if (props.id != null && props.id !== '')
        throw new Error('Invalid product id')
      skus.value = [createDefaultSku()]
      return
    }
    const data = await getProductSpu(formId.value)
    if (!data?.id)
      throw new Error('Product not found')
    formData.value = { ...data, sliderPicUrls: data.sliderPicUrls || [], deliveryTypes: data.deliveryTypes || [] }
    originalDescription.value = data.description || ''
    originalDescriptionText.value = descriptionToText(originalDescription.value)
    descriptionText.value = originalDescriptionText.value
    originalSkus.value = (data.skus || []).map(sku => ({ ...sku }))
    skus.value = (data.skus || []).map(toYuanSku)
  } catch {
    loadFailed.value = true
  } finally {
    detailLoading.value = false
  }
}

/** 构造提交数据 */
function buildSubmitData(): ProductSpu {
  const data = formData.value
  return {
    ...data,
    id: formId.value,
    sliderPicUrls: data.sliderPicUrls || [],
    deliveryTypes: data.deliveryTypes || [],
    skus: skus.value.map(toCentSku),
  }
}

/** 提交表单 */
async function handleSubmit() {
  if (formLoading.value || detailLoading.value || loadFailed.value || !canSave.value)
    return
  const result = await formRef.value?.validate()
  if (!result?.valid) {
    await revealError(result?.errors?.[0]?.prop)
    return
  }
  const data = buildSubmitData()
  if (!data.skus?.length) {
    await revealError('specType')
    toast.warning(formData.value.specType ? '请添加规格并生成 SKU' : '请完善 SKU 价格库存')
    return
  }
  // 逐 SKU 校验销售价/市场价大于 0，避免 0 价商品
  if (skus.value.some(sku => !(Number(sku.price) >= 0.01) || !(Number(sku.marketPrice) >= 0.01))) {
    settingsOpen.value = true
    await revealError('specType')
    toast.warning('请完善 SKU 的销售价与市场价（需大于 0）')
    return
  }
  formLoading.value = true
  try {
    if (formId.value) {
      await updateProductSpu(data)
      toast.success('修改成功')
    } else {
      await createProductSpu(data)
      toast.success('新增成功')
    }
    uni.$emit('mall:product-spu:reload')
    delay(handleBack)
  } catch {
    toast.error('保存失败，已保留输入，请重试')
  } finally {
    formLoading.value = false
  }
}

/** 初始化 */
onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.product-edit {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f4f6f2;
}
.edit-content {
  max-width: 880px;
  width: 100%;
  margin: 0 auto;
  padding: 20px 16px;
  box-sizing: border-box;
}
.edit-card {
  padding: 20px;
  background: white;
  border-radius: 12px;
  margin-bottom: 14px;
}
.section-title {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #263c2e;
  margin-bottom: 16px;
}
.settings-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0;
  margin: 0;
  background: transparent;
  color: #46634e;
  font-size: 15px;
  line-height: 1.6;
  text-align: left;
}
.settings-toggle::after,
.save-button::after,
.text-button::after {
  border: none;
}
.settings-fields {
  margin-top: 16px;
}
.edit-footer {
  flex-shrink: 0;
  border-top: 1px solid #e7ece3;
  background: white;
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
}
.save-button {
  max-width: 848px;
  margin: 0 auto;
  border-radius: 8px;
  background: #2e644b;
  color: white;
  font-size: 15px;
  line-height: 44px;
}
.edit-message {
  padding: 40px 0;
  text-align: center;
  color: #66776b;
}
.text-button {
  background: transparent;
  color: #2e644b;
  font-size: 14px;
}
@media (max-width: 480px) {
  .edit-content {
    padding: 12px;
  }
  .edit-card {
    padding: 16px;
  }
}
</style>

<style scoped>
.description-input :deep(textarea) {
  min-height: 160px;
  line-height: 1.8;
  font-size: 15px;
}
</style>
