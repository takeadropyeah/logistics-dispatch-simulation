import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 地图接口地址通过 .env.local 配置；外围图表沿用 dashboard 的示例数据源。
export default defineConfig({
  plugins: [vue()],
  base: './',
  cacheDir: '.vite',
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/echarts/') || id.includes('/zrender/')) return 'charts'
          if (id.includes('/node_modules/')) return 'vendor'
        },
      },
    },
    chunkSizeWarningLimit: 800,
  },
})
