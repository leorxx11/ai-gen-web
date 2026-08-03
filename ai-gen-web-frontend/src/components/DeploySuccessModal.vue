<script setup lang="ts">
defineProps<{
  url: string
}>()

const open = defineModel<boolean>('open', { default: false })
</script>

<template>
  <a-modal v-model:open="open" title="部署成功" :footer="null">
    <div class="deploy-success">
      <p>应用已部署成功，可以通过以下地址访问：</p>
      <a-typography-paragraph copyable>{{ url }}</a-typography-paragraph>
      <a-button type="primary" :href="url" target="_blank">访问网站</a-button>
    </div>
  </a-modal>
</template>

<style scoped>
.deploy-success {
  text-align: center;
}
</style>
