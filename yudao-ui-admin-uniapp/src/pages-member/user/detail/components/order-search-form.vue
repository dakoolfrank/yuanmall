<template>
  <wd-search v-model="keyword" placeholder="搜索订单号" placeholder-left @search="handleSearch" @clear="handleReset">
    <template #suffix>
      <view class="member-record-search" @click="handleSearch">
        搜索
      </view>
    </template>
  </wd-search>
</template>

<script lang="ts" setup>
import { ref } from 'vue'

const emit = defineEmits<{
  search: [data: Record<string, any>]
  reset: []
}>()
const keyword = ref('')
function handleSearch() {
  const value = keyword.value.trim()
  if (value) {
    emit('search', { no: value })
  } else {
    emit('reset')
  }
}
function handleReset() {
  keyword.value = ''
  emit('reset')
}
</script>

<style scoped>
.member-record-search {
  padding: 8px 12px;
  color: #3d624c;
  font-size: 14px;
}
</style>
