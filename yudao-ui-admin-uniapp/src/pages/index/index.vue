<script lang="ts" setup>
import { computed, ref } from 'vue'
import PasswordForm from '@/pages-core/user/security/components/password-form.vue'
import { useAccess } from '@/hooks/useAccess'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import { useUserStore } from '@/store/user'

definePage({ type: 'home', style: { navigationStyle: 'custom' } })
const userStore = useUserStore()
const tokenStore = useTokenStore()
const { hasAccessByCodes } = useAccess()
const showPassword = ref(false)
const loggingOut = ref(false)
const entries = computed(() => [
  { title: '商品', permission: 'product:spu:query', path: '/pages-mall/product/spu/index' },
  { title: '订单', permission: 'trade:order:query', path: '/pages-mall/trade/order/index' },
  { title: '会员', permission: 'member:user:query', path: '/pages-member/user/index' },
  { title: '设置', permission: 'trade:delivery:pick-up-store:query', path: '/pages-mall/trade/delivery/pick-up-store/index' },
].filter(item => hasAccessByCodes([item.permission])))
function openPage(path: string) {
  uni.navigateTo({ url: path })
}
function confirmLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号？',
    success: async (result) => {
      if (!result.confirm || loggingOut.value)
        return
      loggingOut.value = true
      try {
        await tokenStore.logout()
        uni.reLaunch({ url: LOGIN_PAGE })
      } catch {
        uni.showToast({ title: '退出失败，请重试', icon: 'none' })
      } finally {
        loggingOut.value = false
      }
    },
  })
}
</script>

<template>
  <view class="shop-home">
    <view class="shop-home__content">
      <view class="shop-home__brand">
        <image src="/static/brand/tuantuanyuanyuan-logo.png" mode="aspectFit" class="shop-home__logo" />
        <text class="shop-home__title">团团园园杂货铺</text>
      </view>
      <view class="shop-home__entries">
        <button v-for="item in entries" :key="item.path" class="shop-home__entry" @click="openPage(item.path)">
          <text>{{ item.title }}</text><text class="shop-home__arrow">›</text>
        </button>
        <text v-if="!entries.length" class="shop-home__empty">当前账号暂无可用管理权限</text>
      </view>
      <view class="shop-home__account">
        <text>{{ userStore.userInfo.username }}</text>
        <view class="shop-home__actions">
          <button @click="showPassword = true">
            修改密码
          </button>
          <button :disabled="loggingOut" @click="confirmLogout">
            退出登录
          </button>
        </view>
      </view>
    </view>
    <PasswordForm v-if="showPassword" v-model="showPassword" />
  </view>
</template>

<style scoped>
.shop-home {
  min-height: 100vh;
  box-sizing: border-box;
  background: #f4f6f2;
  padding: 56px 20px 32px;
  color: #233a2d;
}
.shop-home__content {
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
}
.shop-home__brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 32px;
}
.shop-home__logo {
  width: 56px;
  height: 56px;
}
.shop-home__title {
  font-size: 24px;
  font-weight: 600;
}
.shop-home__entries {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.shop-home__entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin: 0;
  padding: 20px 24px;
  border: 1px solid #e0e7df;
  border-radius: 12px;
  background: white;
  color: #233a2d;
  font-size: 20px;
  line-height: 1.5;
  text-align: left;
}
.shop-home__entry::after,
.shop-home__actions button::after {
  border: none;
}
.shop-home__entry:active {
  background: #eaf0e7;
}
.shop-home__arrow {
  color: #788b7a;
}
.shop-home__account {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 32px;
  color: #69786b;
  font-size: 14px;
}
.shop-home__actions {
  display: flex;
  gap: 20px;
}
.shop-home__actions button {
  margin: 0;
  padding: 0;
  background: transparent;
  color: #46634e;
  font-size: 14px;
  line-height: 2;
}
.shop-home__empty {
  font-size: 14px;
}
@media (max-width: 480px) {
  .shop-home {
    padding: 32px 20px;
  }
  .shop-home__title {
    font-size: 22px;
  }
}
</style>
