import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import pkg from './package.json'

const baseDir = new URL('.', import.meta.url).pathname

// https://vite.dev/config/
export default defineConfig({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  plugins: [vue() as any],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
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
  // SPEC-CMS-NOTIFICATION-WS-001 — @stomp/stompjs·sockjs-client(CJS 하이브리드)를
  // 서버 시작 시 사전 번들링해 첫 admin 페이지 요청에서 late dependency discovery로 인한
  // Vite full-reload/재최적화 지연이 발생하지 않도록 한다.
  optimizeDeps: {
    include: ['@stomp/stompjs', 'sockjs-client'],
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
