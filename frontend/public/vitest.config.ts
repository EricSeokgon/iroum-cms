// Vitest 전용 설정 — jest-axe setup, jsdom 환경, 커버리지 게이트
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@iroum/shared': fileURLToPath(new URL('../shared/src', import.meta.url)),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.spec.ts'],
    exclude: ['tests/e2e/**'],
    testTimeout: 15000,
    coverage: {
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/main.ts', 'src/env.d.ts', 'src/i18n.ts'],
    },
  },
})
