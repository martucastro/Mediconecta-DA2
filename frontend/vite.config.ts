import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: '/mediconecta/',
  build: {
    outDir: '../src/main/webapp',
    emptyOutDir: false,
  },
  server: {
    proxy: {
      '/mediconecta/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})