<script setup lang="ts">
import AppCard from '@/components/AppCard.vue'

defineProps<{
  // 区块标题
  title: string
  description?: string
  loading?: boolean
  error?: boolean
  featured?: boolean
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
      <div>
        <h2 class="section-title">
          {{ title }} <span v-if="total">{{ total }}</span>
        </h2>
        <p class="section-desc">{{ description }}</p>
      </div>
      <a-input-search
        v-model:value="searchText"
        class="section-search"
        :placeholder="searchPlaceholder ?? '搜索应用'"
        allow-clear
        @search="emit('search')"
      />
    </div>
    <div v-if="loading" class="app-grid" aria-label="正在加载作品" aria-busy="true">
      <div v-for="item in 3" :key="item" class="skeleton-card">
        <div class="skeleton-cover"></div>
        <a-skeleton active :paragraph="{ rows: 2 }" />
      </div>
    </div>
    <div v-else-if="error" class="list-error" role="alert">作品加载失败，请稍后重新搜索。</div>
    <div v-else-if="apps.length > 0" class="app-grid">
      <AppCard
        v-for="app in apps"
        :key="app.id"
        :app="app"
        :featured="featured"
        @click="emit('cardClick', $event)"
      />
    </div>
    <a-empty v-else :description="emptyText ?? '暂无应用'">
      <!-- 空状态下的自定义操作，如登录按钮 -->
      <slot name="empty-extra" />
    </a-empty>
    <div v-if="!loading && !error && total > (pageSize ?? 6)" class="section-pagination">
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
  margin-top: 48px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}
.section-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.04em;
}
.section-title span {
  margin-left: 8px;
  color: var(--app-muted);
  font-size: 13px;
  font-weight: 400;
}
.section-desc {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--app-muted);
}
.section-search {
  width: 240px;
  flex-shrink: 0;
}
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.section-pagination {
  margin-top: 24px;
  text-align: right;
}
.skeleton-card {
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  background: var(--app-surface);
}
.skeleton-cover {
  height: 190px;
  background: var(--app-soft);
}
.skeleton-card :deep(.ant-skeleton) {
  padding: 20px;
}
.list-error {
  padding: 40px;
  text-align: center;
  color: #a33b32;
}
:deep(.ant-empty) {
  margin: 0;
  padding: 44px 24px;
  border: 1px dashed #cbd9cf;
  border-radius: var(--app-radius);
  background: rgb(255 255 255 / 55%);
}
@media (max-width: 960px) {
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 620px) {
  .app-grid {
    grid-template-columns: 1fr;
  }
  .section-header {
    align-items: stretch;
    flex-direction: column;
    gap: 14px;
  }
  .section-search {
    width: 100%;
  }
  .app-section {
    margin-top: 36px;
  }
}
</style>
