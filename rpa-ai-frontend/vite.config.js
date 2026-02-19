import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    // 使用Vite默认配置（localhost:5173）
    proxy: {
      '/api': {
        target: 'http://localhost:8080/rpa-ai',
        changeOrigin: true
        // 不需要rewrite，路径会自动匹配
      }
    }
  }
})