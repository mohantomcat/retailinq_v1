import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('@mui/x-date-pickers') || id.includes('dayjs')) {
            return 'vendor-datepickers'
          }
          if (id.includes('recharts')) {
            return 'vendor-recharts'
          }
          if (id.includes('html2canvas')) {
            return 'vendor-html2canvas'
          }
          return undefined
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        secure: false
      }
    }
  }
})
