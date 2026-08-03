<script setup lang="ts">
import AppCard from '@/components/AppCard.vue'

defineProps<{
  // 区块标题
  title: string
  apps: API.AppVO[]
  total: number
  pageSize?: number
  searchPlaceholder?: string
  emptyText?: string
}>()

// 搜索关键词与页码由父组件的查询参数托管
const searchText = defineModel<string>('searchText')
const pageNum = defineModel<number>('pageNum')

const emit = defineEmits<{
  search: []
  pageChange: []
  cardClick: [app: API.AppVO]
}>()
</script>

<template>
  <div class="app-section">
    <div class="section-header">
      <h2 class="section-title">{{ title }}</h2>
      <a-input-search
        v-model:value="searchText"
        class="section-search"
        :placeholder="searchPlaceholder ?? '搜索应用'"
        allow-clear
        @search="emit('search')"
      />
    </div>
    <div v-if="apps.length > 0" class="app-grid">
      <AppCard v-for="app in apps" :key="app.id" :app="app" @click="emit('cardClick', $event)" />
    </div>
    <a-empty v-else :description="emptyText ?? '暂无应用'">
      <!-- 空状态下的自定义操作，如登录按钮 -->
      <slot name="empty-extra" />
    </a-empty>
    <div v-if="total > (pageSize ?? 6)" class="section-pagination">
      <a-pagination
        v-model:current="pageNum"
        :page-size="pageSize ?? 6"
        :total="total"
        :show-size-changer="false"
        @change="emit('pageChange')"
      />
    </div>
  </div>
</template>

<style scoped>
.app-section {
  margin-top: 56px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
}

.section-search {
  width: 240px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.section-pagination {
  margin-top: 24px;
  text-align: center;
}
</style>
