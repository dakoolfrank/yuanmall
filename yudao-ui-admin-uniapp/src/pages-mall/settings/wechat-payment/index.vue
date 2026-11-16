<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getPayAppList } from '@/api/pay/app'
import { getPayChannelList } from '@/api/pay/channel'
import { navigateBackPlus } from '@/utils'
import { useAccess } from '@/hooks/useAccess'

definePage({ style: { navigationStyle: 'custom' } })
const { hasAccessByCodes } = useAccess()
const appId = ref<number>()
const loading = ref(true)
const failed = ref(false)
const configured = ref(false)
const enabled = ref(false)
async function load() {
  loading.value = true
  failed.value = false
  try {
    const app = (await getPayAppList()).find(item => item.appKey === 'mall')
    appId.value = app?.id
    if (app?.id) {
      const channel = (await getPayChannelList(app.id)).find(item => item.code === 'wx_lite')
      configured.value = !!channel
      enabled.value = app.status === 0 && channel?.status === 0
    }
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
function configure() {
  if (appId.value)
    uni.navigateTo({ url: `/pages-pay/app/channel/form/index?appId=${appId.value}&code=wx_lite` })
}
onMounted(load)
</script>

<template>
  <view class="payment-settings">
    <wd-navbar title="微信收款设置" left-arrow placeholder safe-area-inset-top fixed @click-left="navigateBackPlus('/pages/index/index')" />
    <view class="payment-card">
      <text class="payment-title">商城 · 微信小程序支付</text>
      <text v-if="loading">正在检查配置…</text>
      <template v-else-if="failed">
        <text>配置读取失败</text><wd-button @click="load">
          重试
        </wd-button>
      </template>
      <template v-else-if="appId">
        <text>{{ configured ? (enabled ? '渠道已启用，尚需实际支付验收' : '渠道已配置但未启用') : '尚未配置微信小程序支付渠道' }}</text>
        <text class="payment-note">在渠道配置中填写实际商户号、API 密钥及证书。配置存在不代表已完成微信扣款联调。</text>
        <wd-button v-if="hasAccessByCodes([configured ? 'pay:channel:update' : 'pay:channel:create'])" @click="configure">
          {{ configured ? '编辑微信收款配置' : '配置微信收款' }}
        </wd-button>
      </template>
      <text v-else>没有找到商城支付应用，请先在支付应用中创建应用标识为 mall 的应用。</text>
      <view class="payment-note">
        现金或普通收款码到账后，在对应见面付款订单中确认收款；此处配置的是顾客下单时的微信在线支付。
      </view>
    </view>
  </view>
</template>

<style scoped>
.payment-settings {
  min-height: 100vh;
  background: #f4f6f2;
}
.payment-card {
  max-width: 800px;
  margin: 20px auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  background: white;
  border-radius: 12px;
}
.payment-title {
  font-size: 18px;
  font-weight: 600;
}
.payment-note {
  font-size: 14px;
  color: #6c786d;
  line-height: 1.7;
}
@media (max-width: 480px) {
  .payment-card {
    margin: 12px;
    padding: 20px;
  }
}
</style>
