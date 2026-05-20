import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/main.css'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { apiClient } from '@iroum/shared/api/client'
import { useAuthStore } from '@/stores/auth'
import type { RefreshResult } from '@iroum/shared/types/api'

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()

  // pinia, i18n, ElementPlus 먼저 등록
  app.use(pinia)
  app.use(i18n)
  app.use(ElementPlus)

  // 라우터 설치 전에 세션 복원:
  // app.use(router) 시점에 초기 네비게이션이 즉시 실행되므로
  // 그 전에 accessToken을 채워야 인증 가드가 정상 통과함
  const auth = useAuthStore()
  try {
    const res = await apiClient.post<RefreshResult>('/auth/refresh', null)
    auth._applyToken(res.data.accessToken, res.data.accessExpiresInSeconds)
  } catch {
    // 유효한 리프레시 토큰 없음 — 로그인 필요
  }

  // 세션 복원 완료 후 라우터 설치
  app.use(router)
  app.mount('#app')
}

bootstrap()
