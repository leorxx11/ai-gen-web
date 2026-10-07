import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端地址，开发和 preview 时通过代理转发 /api，保证预览 iframe 与主站同源
const backendProxy = {
  '/api': {
    target: process.env.VITE_PROXY_TARGET || 'http://localhost:8123',
    changeOrigin: true,
    secure: false,
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: backendProxy,
  },
  preview: {
    proxy: backendProxy,
  },
})
