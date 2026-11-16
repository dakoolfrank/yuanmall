<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http } from '@/http/http'
import { useAccess } from '@/hooks/useAccess'
import { navigateBackPlus } from '@/utils'

definePage({ style: { navigationStyle: 'custom' } })
const { hasAccessByCodes } = useAccess()
const loading = ref(true)
const saving = ref(false)
const failed = ref(false)
const syncing = ref(false)
const syncError = ref('')
const accounts = ref<{ deliveryId: string, bizId: string, alias?: string, statusCode?: number }[]>([])
const form = reactive({ senderName: '', senderMobile: '', senderProvince: '', senderCity: '', senderDistrict: '', senderAddress: '', deliveryId: '', bizId: '', serviceType: undefined as number | undefined })
const fields = [
  { key: 'senderName', label: '寄件人' },
  { key: 'senderMobile', label: '联系电话' },
  { key: 'senderProvince', label: '省份' },
  { key: 'senderCity', label: '城市' },
  { key: 'senderDistrict', label: '区县' },
  { key: 'senderAddress', label: '详细地址' },
  { key: 'deliveryId', label: '快递代码' },
  { key: 'bizId', label: '合作账号' },
] as const
async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await http.get<Partial<typeof form>>('/trade/delivery/wechat/config')
    for (const { key } of fields) {
      form[key] = data[key] || ''
    }
    form.serviceType = data.serviceType
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
async function save() {
  if (saving.value || loading.value || failed.value || !hasAccessByCodes(['trade:delivery:express-template:update']))
    return
  saving.value = true
  try {
    await http.put('/trade/delivery/wechat/config', { ...form })
    uni.showToast({ title: '设置已保存', icon: 'success' })
  } catch { /* 请求层提示失败，保留输入。 */ } finally { saving.value = false }
}
async function sync() {
  if (syncing.value)
    return
  syncing.value = true
  syncError.value = ''
  try {
    accounts.value = await http.get<typeof accounts.value>('/trade/delivery/wechat/accounts')
    if (!accounts.value.length)
      syncError.value = '尚无绑定账号，请在顾客小程序后台开通物流助手并绑定快递账号。'
  } catch {
    syncError.value = '同步失败，请核对顾客小程序配置、物流助手开通和快递合作账号。'
  } finally {
    syncing.value = false
  }
}
function selectAccount(account: typeof accounts.value[number]) {
  form.deliveryId = account.deliveryId
  form.bizId = account.bizId
}
function openFreightRules() {
  uni.navigateTo({ url: '/pages-mall/trade/delivery/express-template/index' })
}
onMounted(load)
</script>

<template>
  <view class="logistics-settings">
    <wd-navbar title="微信物流设置" left-arrow placeholder safe-area-inset-top fixed @click-left="navigateBackPlus('/pages/index/index')" />
    <scroll-view scroll-y class="settings-scroll">
      <view class="settings-content">
        <view class="settings-card settings-note">
          店铺自己送：免费配送。以下配置用于快递寄件，与顾客结算时收取的运费分开。
        </view>
        <view class="settings-card freight-settings">
          <text class="settings-title">快递配送运费</text>
          <view class="settings-note">
            设置顾客下单时支付的快递费，支持按地区、首重及续重计费。店铺自己送仍免费。
          </view>
          <wd-button variant="plain" @click="openFreightRules">
            设置快递运费
          </wd-button>
        </view>
        <view v-if="loading" class="settings-card">
          正在加载…
        </view>
        <view v-else-if="failed" class="settings-card">
          设置加载失败 <button @click="load">
            重试
          </button>
        </view>
        <template v-else>
          <view class="settings-card">
            <text class="settings-title">寄件与快递合作信息</text>
            <wd-cell-group border>
              <wd-input v-for="field in fields" :key="field.key" v-model="form[field.key]" :label="field.label" label-width="88px" :placeholder="`填写${field.label}`" clearable />
              <wd-input-number v-model="form.serviceType" label="服务类型" :min="0" />
            </wd-cell-group>
            <view class="settings-note">
              快递代码、合作账号和服务类型以物流助手返回的信息为准，不能随意填写。设置保存不代表已经生成运单。
            </view>
          </view>
          <view class="settings-card">
            <text class="settings-title">微信物流账号</text>
            <view class="settings-note">
              先在顾客小程序后台开通物流助手、绑定快递合作账号，再同步。小程序 AppID / AppSecret 由已有小程序配置管理，不填写在此处。
            </view>
            <wd-button :loading="syncing" @click="sync">
              同步绑定账号
            </wd-button>
            <view v-if="syncError" class="settings-note">
              {{ syncError }}
            </view>
            <button v-for="account in accounts" :key="account.deliveryId + account.bizId" class="account-button" @click="selectAccount(account)">
              {{ account.alias || account.deliveryId }} · {{ account.bizId }}（点击选用）
            </button>
          </view>
          <view class="settings-card settings-note">
            当前可保存寄件设置、同步微信绑定账号。自动生成电子面单仍需完成运单接口与真实账号联调；现阶段订单可填写已有快递单号发货。
          </view>
        </template>
      </view>
    </scroll-view>
    <view class="settings-footer">
      <wd-button v-if="hasAccessByCodes(['trade:delivery:express-template:update'])" block :disabled="loading || failed || saving" :loading="saving" @click="save">
        保存设置
      </wd-button>
    </view>
  </view>
</template>

<style scoped>
.logistics-settings {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f4f6f2;
}
.settings-scroll {
  min-height: 0;
  flex: 1;
}
.settings-content {
  max-width: 880px;
  margin: 0 auto;
  padding: 16px;
}
.settings-card {
  background: white;
  padding: 20px;
  border-radius: 12px;
  margin-bottom: 14px;
}
.settings-title {
  display: block;
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 16px;
}
.settings-note {
  font-size: 14px;
  color: #6c786d;
  line-height: 1.7;
  margin-top: 12px;
}
.settings-footer {
  padding: 12px 16px;
  background: white;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
}
.settings-footer > :deep(view) {
  max-width: 848px;
  margin: 0 auto;
}
.account-button {
  margin-top: 12px;
  font-size: 14px;
}
</style>
