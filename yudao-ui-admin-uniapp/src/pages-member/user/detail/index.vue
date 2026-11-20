<template>
  <view class="yd-page-container yd-page-container-paging member-detail">
    <wd-navbar title="会员详情" left-arrow placeholder safe-area-inset-top fixed @click-left="handleBack" />
    <view class="member-detail__tabs">
      <wd-tabs v-model="tabIndex">
        <wd-tab v-for="(detailTab, index) in tabs" :key="detailTab.key" :name="String(index)" :title="detailTab.title" />
      </wd-tabs>
    </view>

    <view v-if="activeTab === 'basic'" class="member-detail__profile">
      <view v-if="formData" class="member-profile">
        <text class="member-profile__name">{{ formData.name || formData.nickname || `会员 ${memberId}` }}</text>
        <text v-if="formData.status === 1" class="member-profile__disabled">已停用</text>
        <view v-if="formData.name && formData.nickname && formData.name !== formData.nickname" class="member-profile__field">
          <text class="member-profile__label">昵称</text><text>{{ formData.nickname }}</text>
        </view>
        <view class="member-profile__field">
          <text class="member-profile__label">手机号</text><text>{{ formData.mobile || '未绑定手机号' }}</text>
        </view>
        <view class="member-profile__field">
          <text class="member-profile__label">备注</text><text>{{ formData.mark || '未填写' }}</text>
        </view>
      </view>
    </view>

    <OrderList
      v-if="loadedTabs.has('order') && hasAccessByCodes(['trade:order:query'])"
      v-show="activeTab === 'order'"
      class="min-h-0 flex-1"
      :user-id="memberId"
    />
    <view v-if="activeTab === 'basic' && hasAccessByCodes(['member:user:update'])" class="member-detail__footer">
      <button class="member-detail__edit" @click="handleEdit">
        编辑资料
      </button>
    </view>
  </view>
</template>

<script lang="ts" setup>
import type { MemberUser } from '@/api/member/user'
import { onUnload } from '@dcloudio/uni-app'
import { computed, onMounted, ref, watch } from 'vue'
import { getMemberUser } from '@/api/member/user'
import { useAccess } from '@/hooks/useAccess'
import { navigateBackPlus } from '@/utils'
import { parseId } from '@/utils/id'
import OrderList from './components/order-list.vue'

const props = defineProps<{ id?: number | string, tab?: string }>()
definePage({ style: { navigationBarTitleText: '', navigationStyle: 'custom' } })

const { hasAccessByCodes } = useAccess()
const memberId = computed(() => parseId(props.id))
const formData = ref<MemberUser>()
const tabs = computed(() => [
  { key: 'basic', title: '资料', permission: undefined },
  { key: 'order', title: '订单', permission: 'trade:order:query' },
].filter(item => !item.permission || hasAccessByCodes([item.permission])))
const initialTabIndex = Math.max(0, tabs.value.findIndex(item => item.key === props.tab))
const tabIndex = ref(String(initialTabIndex))
const activeTab = computed(() => tabs.value[Number(tabIndex.value)]?.key || 'basic')
const loadedTabs = ref(new Set<string>([activeTab.value]))
watch(activeTab, value => loadedTabs.value.add(value))

function handleBack() {
  navigateBackPlus('/pages-member/user/index')
}

async function getDetail() {
  if (memberId.value) {
    formData.value = await getMemberUser(memberId.value)
  }
}

function handleEdit() {
  if (memberId.value) {
    uni.navigateTo({ url: `/pages-member/user/form/index?id=${memberId.value}` })
  }
}

onMounted(() => {
  uni.$on('member:user:reload', getDetail)
  getDetail()
})
onUnload(() => uni.$off('member:user:reload', getDetail))
</script>

<style scoped>
.member-detail {
  background: #f4f6f2;
}
.member-detail__tabs {
  flex-shrink: 0;
  background: #fff;
}
.member-detail__profile {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 16px;
}
.member-profile {
  max-width: 920px;
  margin: 0 auto;
  padding: 24px;
  border-radius: 12px;
  background: #fff;
  color: #233a2d;
}
.member-profile__name {
  display: block;
  margin-bottom: 20px;
  overflow-wrap: anywhere;
  font-size: 22px;
  font-weight: 600;
}
.member-profile__field {
  display: flex;
  gap: 20px;
  padding: 16px 0;
  border-top: 1px solid #eef1eb;
  overflow-wrap: anywhere;
  font-size: 15px;
}
.member-profile__label {
  flex-shrink: 0;
  width: 60px;
  color: #798477;
}
.member-profile__disabled {
  display: block;
  margin-bottom: 12px;
  color: #a05d37;
  font-size: 14px;
}
.member-detail__footer {
  flex-shrink: 0;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  background: #fff;
}
.member-detail__edit {
  max-width: 960px;
  margin: 0 auto;
  padding: 12px;
  border-radius: 8px;
  background: #3d624c;
  color: #fff;
  font-size: 16px;
  line-height: 1.5;
}
.member-detail__edit::after {
  border: none;
}
</style>
