<template>
  <view class="member-search">
    <wd-search
      v-model="keyword"
      placeholder="搜索昵称或手机号"
      placeholder-left
      @search="handleSearch"
      @clear="handleReset"
    >
      <template #suffix>
        <view class="member-search__submit" @click="handleSearch">
          搜索
        </view>
      </template>
    </wd-search>
  </view>
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
  if (!value) {
    emit('reset')
    return
  }
  emit('search', /^1[3-9]\d{1,9}$/.test(value) ? { mobile: value } : { nickname: value })
}

function handleReset() {
  keyword.value = ''
  emit('reset')
}
</script>

<style scoped>
.member-search {
  background: #fff;
}
.member-search__submit {
  padding: 8px 12px;
  color: #3d624c;
  font-size: 14px;
}
</style>
