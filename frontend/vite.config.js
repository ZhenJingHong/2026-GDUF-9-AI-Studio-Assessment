import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
const proxy = { '/api': { target: process.env.API_TARGET || 'http://localhost:8080', changeOrigin: true } }
export default defineConfig({ plugins: [vue()], server: { proxy }, preview: { proxy } })
