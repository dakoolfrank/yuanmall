<template>
  <view class="auth-container shop-login">
    <view class="shop-login__card">
      <view class="shop-login__brand">
        <image class="shop-login__logo" src="/static/brand/yuanyuan-logo.png" mode="aspectFit" />
        <text class="shop-login__title">{{ title }}</text>
      </view>
      <view class="form-container">
        <view class="input-item">
          <wd-icon name="user" size="20px" color="#54816b" />
          <wd-input
            v-model="formData.username"
            placeholder="请输入账号"
            clearable
            clear-trigger="focus"
            :disabled="loading"
            @confirm="handleLogin"
          />
        </view>
        <view class="input-item">
          <wd-icon name="lock" size="20px" color="#54816b" />
          <wd-input
            v-model="formData.password"
            placeholder="请输入密码"
            clearable
            clear-trigger="focus"
            show-password
            confirm-type="go"
            :disabled="loading"
            @confirm="handleLogin"
          />
        </view>
        <view v-if="tenantError" class="shop-login__error" role="alert">
          <text>{{ tenantError }}</text>
          <button class="shop-login__retry" @click="loadTenant">
            重试
          </button>
        </view>
        <wd-button
          block
          :disabled="loading || !tenantReady"
          :loading="loading || initializing"
          type="primary"
          @click="handleLogin"
        >
          登录
        </wd-button>
      </view>
    </view>
    <Verify
      v-if="captchaEnabled && captchaMounted"
      ref="verifyRef"
      captcha-type="blockPuzzle"
      explain="向右滑动完成验证"
      :img-size="{ width: '300px', height: '150px' }"
      mode="pop"
      @success="verifySuccess"
    />
  </view>
</template>

<script lang="ts" setup>
import { useToast } from '@wot-ui/ui/components/wd-toast'
import { nextTick, reactive, ref } from 'vue'
import { useTokenStore } from '@/store/token'
import { ensureDecodeURIComponent, redirectAfterLogin } from '@/utils'
import Verify from './components/verifition/verify.vue'
import { useLoginTenant } from './useLoginTenant'

defineOptions({ name: 'LoginPage' })
const props = defineProps<{ redirect?: string }>()
definePage({ style: { navigationStyle: 'custom' } })
const title = import.meta.env.VITE_APP_TITLE || '团团园园后台管理'
const toast = useToast()
const loading = ref(false)
const captchaEnabled = import.meta.env.VITE_APP_CAPTCHA_ENABLE === 'true'
const captchaMounted = ref(false)
const verifyRef = ref<InstanceType<typeof Verify>>()
const { initializing, ready: tenantReady, error: tenantError, loadTenant } = useLoginTenant()
const formData = reactive({ username: '', password: '', captchaVerification: '' })

async function handleLogin() {
  if (loading.value || !tenantReady.value)
    return
  if (!formData.username.trim()) {
    toast.warning('请输入账号')
    return
  }
  if (!formData.password) {
    toast.warning('请输入密码')
    return
  }
  if (captchaEnabled) {
    captchaMounted.value = true
    await nextTick()
    verifyRef.value?.show()
  } else {
    await verifySuccess({})
  }
}

async function verifySuccess(params: { captchaVerification?: string }) {
  if (loading.value || !tenantReady.value)
    return
  loading.value = true
  try {
    const tokenStore = useTokenStore()
    await tokenStore.login({
      type: 'username',
      username: formData.username.trim(),
      password: formData.password,
      captchaVerification: params.captchaVerification,
    })
    redirectAfterLogin(props.redirect ? ensureDecodeURIComponent(props.redirect) : undefined)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.shop-login {
  --wot-button-primary-bg: #2e644b;
  --wot-button-primary-bg-active: #214e39;
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: #f4f6f2;
  box-sizing: border-box;

  &__card {
    width: 100%;
    max-width: 420px;
    padding: 34px 32px 32px;
    border: 1px solid #e7ebe3;
    border-radius: 18px;
    background: #fff;
    box-shadow: 0 12px 48px rgb(38 65 45 / 5%);
    box-sizing: border-box;
  }

  &__brand {
    display: flex;
    align-items: center;
    flex-direction: column;
    margin-bottom: 30px;
  }

  &__logo {
    width: 84px;
    height: 84px;
    margin-bottom: 18px;
    flex-shrink: 0;
  }

  &__title {
    color: #284333;
    font-size: 21px;
    font-weight: 600;
    line-height: 1.5;
    text-align: center;
  }

  .form-container {
    width: 100%;
  }

  .input-item {
    display: flex;
    min-height: 52px;
    align-items: center;
    padding: 0 14px;
    margin-bottom: 16px;
    border: 1px solid #e2e8df;
    border-radius: 8px;
    background: #f8faf6;

    :deep(.wd-input) {
      min-width: 0;
      flex: 1;
      margin-left: 10px;
      background: transparent;
    }

    :deep(.wd-input__inner) {
      font-size: 15px;
    }
  }

  :deep(.wd-button) {
    height: 46px;
    margin-top: 8px;
    border-radius: 8px;
    font-size: 15px;
  }

  &__error {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 14px;
    color: #9b5c4c;
    font-size: 12px;
  }

  &__retry {
    margin: 0;
    padding: 4px 8px;
    border: 0;
    background: transparent;
    color: #2e644b;
    font-size: 12px;
    line-height: 1.6;
    &::after {
      border: 0;
    }
  }
}

@media (max-width: 480px) {
  .shop-login {
    padding: 24px 16px;
    &__card {
      padding: 28px 24px;
    }
    &__title {
      font-size: 20px;
    }
  }
}
</style>
