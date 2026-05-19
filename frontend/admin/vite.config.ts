import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// import.meta.url은 ESM 모듈에서 현재 파일의 URL을 반환함
// fileURLToPath 없이 pathname 직접 사용 (Linux/WSL 환경)
const baseDir = new URL('.', import.meta.url).pathname

// https://vite.dev/config/
export default defineConfig({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  plugins: [vue() as any],
  define: {
    __APP_VERSION__: JSON.stringify('0.1.1'),
    __BUILD_TIME__: JSON.stringify(new Date().toISOString()),
  },
  resolve: {
    alias: {
      '@': baseDir + 'src',
      '@iroum/shared': baseDir + '../shared/src',
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // refresh_token Cookie의 path=/api/v1/auth 가 로컬 개발에서도 동작하도록
        cookieDomainRewrite: 'localhost',
      },
    },
  },
  build: {
    target: 'es2022',
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['tests/**/*.spec.ts'],
    // E2E Playwright 파일은 vitest 대상에서 제외 — playwright.config.ts에서 별도 실행
    exclude: ['**/node_modules/**', 'tests/e2e/**'],
    setupFiles: ['./tests/setup.ts'],
    coverage: {
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,vue}'],
    },
  },
})
