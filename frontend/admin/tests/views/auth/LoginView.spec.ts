// LoginView 컴포넌트 테스트 — SPEC-CMS-002 REQ-AUTH-001
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import axios from 'axios'

// apiClient mock
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))
vi.mock('@/router', () => ({ default: { push: vi.fn() } }))

import LoginView from '@/views/auth/LoginView.vue'
import { useAuthStore } from '@/stores/auth'

// 최소 i18n
const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      'auth.login.title': '관리자 로그인',
      'auth.login.formLabel': '로그인 폼',
      'auth.login.username': '사용자명',
      'auth.login.usernamePlaceholder': '사용자명',
      'auth.login.password': '비밀번호',
      'auth.login.passwordPlaceholder': '비밀번호',
      'auth.login.submit': '로그인',
      'auth.login.submitting': '로그인 중...',
      'auth.login.error.usernameRequired': '사용자명을 입력해 주세요',
      'auth.login.error.passwordRequired': '비밀번호를 입력해 주세요',
      'auth.login.error.passwordMin': '8자 이상',
      'auth.login.error.invalid': '사용자명 또는 비밀번호가 올바르지 않습니다',
      'auth.login.error.locked': '계정이 잠겼습니다',
      'auth.login.error.network': '서버 오류',
    },
  },
})

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/dashboard', component: { template: '<div />' } },
  ],
})

function mountLoginView() {
  return mount(LoginView, {
    global: {
      // beforeEach에서 setActivePinia로 설정된 pinia를 재사용 — 새 인스턴스를 만들지 않음
      plugins: [router, i18n, ElementPlus],
    },
  })
}

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('폼이 렌더링된다', () => {
    const wrapper = mountLoginView()
    expect(wrapper.find('h1').text()).toContain('관리자 로그인')
    expect(wrapper.find('input[name="username"]').exists()).toBe(true)
    expect(wrapper.find('input[name="password"]').exists()).toBe(true)
  })

  it('필드가 비어있으면 에러 알림이 없다', () => {
    const wrapper = mountLoginView()
    expect(wrapper.find('[data-testid="login-error"]').exists()).toBe(false)
  })

  it('401 응답 시 에러 메시지를 표시한다', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'login').mockRejectedValueOnce(
      Object.assign(axios.create().get('/'), {
        isAxiosError: true,
        response: {
          status: 401,
          data: { code: 'AUTH_INVALID_CREDENTIALS', message: 'bad creds' },
        },
      }),
    )

    const wrapper = mountLoginView()
    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('wrongpass')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="login-error"]').exists()).toBe(true)
  })

  it('423 응답 시 계정 잠김 메시지를 표시한다', async () => {
    const auth = useAuthStore()
    const err = new Error('locked') as Error & { isAxiosError: boolean; response: unknown }
    err.isAxiosError = true
    err.response = {
      status: 423,
      data: { code: 'AUTH_ACCOUNT_LOCKED', message: 'locked' },
    }
    vi.spyOn(auth, 'login').mockRejectedValueOnce(err)

    const wrapper = mountLoginView()
    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('password1')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const errorEl = wrapper.find('[data-testid="login-error"]')
    expect(errorEl.exists()).toBe(true)
    expect(errorEl.text()).toContain('잠겼습니다')
  })

  it('로그인 성공 시 authStore.login을 호출한다', async () => {
    const auth = useAuthStore()
    const loginSpy = vi.spyOn(auth, 'login').mockResolvedValueOnce()

    const wrapper = mountLoginView()
    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('password1')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith('admin', 'password1')
  })
})
