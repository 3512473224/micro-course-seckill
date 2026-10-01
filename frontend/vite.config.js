import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本地开发：/api 代理到本机网关；线上走 nginx.conf 的 proxy_pass（见 Dockerfile 构建）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
