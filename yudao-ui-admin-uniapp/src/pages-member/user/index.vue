<template>
  <view class="yd-page-container yd-page-container-paging members-page">
    <wd-navbar title="会员" left-arrow placeholder safe-area-inset-top fixed @click-left="handleBack" />
    <SearchForm @search="handleQuery" @reset="handleReset" />
    <z-paging
      ref="pagingRef"
      v-model="list"
      :fixed="false"
      class="min-h-0 flex-1"
      :default-page-size="10"
      :refresher-enabled="true"
      :inside-more="true"
      :loading-more-default-as-loading="true"
      empty-view-text="暂无会员"
      @query="queryList"
    >
      <view class="member-list">
        <view v-for="item in list" :key="item.id" class="member-card" @click="handleDetail(item)">
          <view class="member-card__info">
            <text class="member-card__name">{{ item.name || item.nickname || item.mobile || `会员 ${item.id}` }}</text>
            <text v-if="item.mobile" class="member-card__phone">{{ item.mobile }}</text>
            <text v-if="item.mark" class="member-card__remark">{{ item.mark }}</text>
            <text v-if="item.status === 1" class="member-card__disabled">已停用</text>
          </view>
          <button v-if="hasAccessByCodes(['trade:order:query'])" class="member-card__orders" @click.stop="handleOrders(item)">
            查看订单
          </button>
        </view>
      </view>
    </z-paging>
  </view>
</template>

<script lang="ts" setup>
import type { MemberUser } from '@/api/member/user'
import { onUnload } from '@dcloudio/uni-app'
import { onMounted, ref } from 'vue'
import { getMemberUserPage } from '@/api/member/user'
import { useAccess } from '@/hooks/useAccess'
import { navigateBackPlus } from '@/utils'
import SearchForm from './components/search-form.vue'

definePage({ style: { navigationBarTitleText: '', navigationStyle: 'custom' } })

const { hasAccessByCodes } = useAccess()
const list = ref<MemberUser[]>([])
const pagingRef = ref<any>()
const queryParams = ref<Record<string, any>>({})

function handleBack() {
  navigateBackPlus()
}

async function queryList(pageNo: number, pageSize: number) {
  try {
    const data = await getMemberUserPage({ ...queryParams.value, pageNo, pageSize })
    pagingRef.value?.completeByTotal(data.list, data.total)
  } catch {
    pagingRef.value?.complete(false)
  }
}

function handleQuery(data?: Record<string, any>) {
  queryParams.value = { ...data }
  reload()
}

function handleReset() {
  handleQuery()
}

function reload() {
  pagingRef.value?.reload()
}

function handleDetail(item: MemberUser) {
  if (item.id) {
    uni.navigateTo({ url: `/pages-member/user/detail/index?id=${item.id}` })
  }
}

function handleOrders(item: MemberUser) {
  if (item.id) {
    uni.navigateTo({ url: `/pages-member/user/detail/index?id=${item.id}&tab=order` })
  }
}

onMounted(() => uni.$on('member:user:reload', reload))
onUnload(() => uni.$off('member:user:reload', reload))
</script>

<style scoped>
.members-page {
  background: #f4f6f2;
}
.member-list {
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
}
.member-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  padding: 18px 20px;
  border: 1px solid #e4e9e1;
  border-radius: 12px;
  background: #fff;
}
.member-card__info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}
.member-card__name {
  overflow-wrap: anywhere;
  color: #233a2d;
  font-size: 18px;
  font-weight: 600;
}
.member-card__phone,
.member-card__remark,
.member-card__disabled {
  overflow-wrap: anywhere;
  color: #788378;
  font-size: 14px;
}
.member-card__disabled {
  color: #a05d37;
}
.member-card__orders {
  flex-shrink: 0;
  margin: 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: #edf2ea;
  color: #3d624c;
  font-size: 14px;
  line-height: 1.5;
}
.member-card__orders::after {
  border: none;
}
@media (max-width: 480px) {
  .member-list {
    padding: 12px;
  }
  .member-card {
    gap: 10px;
    padding: 16px 14px;
  }
  .member-card__name {
    font-size: 16px;
  }
}
</style>
