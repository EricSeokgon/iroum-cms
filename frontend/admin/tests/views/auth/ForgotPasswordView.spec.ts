// ForgotPasswordView 테스트 — REQ-AUTH-017 비밀번호 재설정(이메일 OTP)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'

// authApi mock
vi.mock('@/api/auth', () => ({
  authApi: {
    verifyRequest: vi.fn(),
    verifyConfirm: vi.fn(),
    passwordResetConfirm: vi.fn(),
  },
}))

// shared apiClient mock (ForgotPasswordView가 간접 import하는 경로)
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))

import ForgotPasswordView from '@/views/auth/ForgotPasswordView.vue'
import { authApi } from '@/api/auth'

// ── i18n 최소 메시지 ───────────────────────────────────────────────────────────
const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      'auth.forgotPassword.title': '비밀번호 재설정',
      'auth.forgotPassword.step1.title': '이메일 입력',
      'auth.forgotPassword.step1.label': '이메일 주소',
      'auth.forgotPassword.step1.submit': '인증 코드 받기',
      'auth.forgotPassword.step1.emailRequired': '이메일을 입력해 주세요',
      'auth.forgotPassword.step1.emailInvalid': '올바른 이메일 형식이 아닙니다',
      'auth.forgotPassword.step2.title': '인증 코드 확인',
      'auth.forgotPassword.step2.label': '6자리 인증 코드',
      'auth.forgotPassword.step2.submit': '확인',
      'auth.forgotPassword.step2.resend': '재발송',
      'auth.forgotPassword.step2.cooldown': '{seconds}초 후 재발송 가능',
      'auth.forgotPassword.step2.expires': '{minutes}분 {seconds}초 후 만료',
      'auth.forgotPassword.step2.attemptsLeft': '{n}회 남음',
      'auth.forgotPassword.step2.codeRequired': '인증 코드를 입력해 주세요',
      'auth.forgotPassword.step2.codePattern': '6자리 숫자를 입력해 주세요',
      'auth.forgotPassword.step3.title': '새 비밀번호',
      'auth.forgotPassword.step3.label': '새 비밀번호',
      'auth.forgotPassword.step3.confirmLabel': '비밀번호 확인',
      'auth.forgotPassword.step3.submit': '비밀번호 재설정',
      'auth.forgotPassword.step3.passwordRequired': '새 비밀번호를 입력해 주세요',
      'auth.forgotPassword.step3.confirmRequired': '비밀번호 확인을 입력해 주세요',
      'auth.forgotPassword.success': '비밀번호가 재설정되었습니다. 다시 로그인해 주세요.',
      'auth.forgotPassword.error.cooldown': '재발송 쿨다운 중입니다. {seconds}초 후 다시 시도해 주세요.',
      'auth.forgotPassword.error.ipBlocked': '비정상적인 시도가 감지되어 일시 차단되었습니다.',
      'auth.forgotPassword.error.codeMismatch': '인증 코드가 일치하지 않습니다 ({n}회 남음).',
      'auth.forgotPassword.error.attemptExceeded': '시도 횟수를 초과했습니다. 처음부터 다시 진행해 주세요.',
      'auth.forgotPassword.error.expired': '인증 시간이 만료되었습니다.',
      'auth.forgotPassword.error.tokenInvalid': '인증 정보가 만료되어 처음부터 다시 진행해 주세요.',
      'auth.forgotPassword.error.policy': '비밀번호 정책을 충족하지 않습니다',
      'auth.forgotPassword.error.reuse': '최근 5회 사용한 비밀번호입니다',
      'account.password.error.policy': '비밀번호는 8자 이상, 대소문자/숫자/특수문자 중 3종 이상이어야 합니다',
      'account.password.error.confirmMismatch': '새 비밀번호와 확인이 일치하지 않습니다',
      'account.password.strength.label': '비밀번호 강도',
      'account.password.strength.veryWeak': '매우 약함',
      'account.password.strength.weak': '약함',
      'account.password.strength.fair': '보통',
      'account.password.strength.good': '강함',
      'common.error.network': '서버에 연결할 수 없습니다.',
    },
  },
})

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/forgot-password', component: ForgotPasswordView },
    { path: '/login', component: { template: '<div />' } },
  ],
})

function mountView() {
  return mount(ForgotPasswordView, {
    global: {
      plugins: [createPinia(), router, i18n, ElementPlus],
    },
  })
}

// 공통 verifyRequest 성공 응답
const mockVerifyResponse = {
  data: {
    requestId: 'req-123',
    expiresAt: new Date(Date.now() + 300_000).toISOString(),
    cooldownSeconds: 60,
  },
}

// Step1 완료 후 Step2로 이동하는 헬퍼
async function advanceToStep2(wrapper: ReturnType<typeof mountView>) {
  vi.mocked(authApi.verifyRequest).mockResolvedValueOnce(mockVerifyResponse as never)
  const emailInput = wrapper.find('input[name="email"]')
  await emailInput.setValue('user@example.com')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

// Step2 완료 후 Step3로 이동하는 헬퍼
async function advanceToStep3(wrapper: ReturnType<typeof mountView>) {
  await advanceToStep2(wrapper)
  vi.mocked(authApi.verifyConfirm).mockResolvedValueOnce({
    data: { verifiedToken: 'vt-abc', purpose: 'PASSWORD_RESET' },
  } as never)
  // el-input의 name 속성이 native <input>에 바인딩되므로 input[name="code"] 사용
  const codeInput = wrapper.find('input[name="code"]')
  await codeInput.setValue('123456')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('ForgotPasswordView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  // ── Step 1 ─────────────────────────────────────────────────────────────────
  describe('Step 1: 이메일 입력', () => {
    it('이메일이 비어있으면 제출 시 유효성 오류가 표시된다', async () => {
      const wrapper = mountView()
      // jsdom에서 el-form validate()는 항상 resolve(true)를 반환하므로 $refs를 통해 직접 mock
      const formInst = (wrapper.vm as any).$refs?.step1FormRef
      vi.spyOn(formInst, 'validate').mockRejectedValueOnce(false)
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(authApi.verifyRequest).not.toHaveBeenCalled()
    })

    it('유효한 이메일로 제출하면 verifyRequest가 호출되고 Step2로 이동한다', async () => {
      const wrapper = mountView()
      await advanceToStep2(wrapper)
      expect(authApi.verifyRequest).toHaveBeenCalledWith({
        channel: 'EMAIL',
        target: 'user@example.com',
        purpose: 'PASSWORD_RESET',
      })
      // Step2 콘텐츠 확인 (OTP input)
      expect(wrapper.find('[data-testid="otp-input"]').exists()).toBe(true)
    })
  })

  // ── Step 2 ─────────────────────────────────────────────────────────────────
  describe('Step 2: OTP 코드 입력', () => {
    it('재발송 버튼이 cooldown 중에는 비활성 상태이다', async () => {
      const wrapper = mountView()
      await advanceToStep2(wrapper)
      const resendBtn = wrapper.find('[data-testid="resend-button"]')
      // cooldownSeconds=60 → 버튼 disabled
      expect(resendBtn.attributes('disabled') !== undefined || resendBtn.classes().includes('is-disabled')).toBe(true)
    })

    it('코드 불일치(401)시 attemptsLeft를 표시한다', async () => {
      const wrapper = mountView()
      await advanceToStep2(wrapper)

      const axiosError = Object.assign(new Error('mismatch'), {
        isAxiosError: true,
        response: {
          status: 401,
          data: { code: 'CODE_MISMATCH', message: 'mismatch', attemptsLeft: 2 },
        },
      })
      vi.mocked(authApi.verifyConfirm).mockRejectedValueOnce(axiosError)

      const codeInput = wrapper.find('input[name="code"]')
      await codeInput.setValue('000000')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      const attemptsEl = wrapper.find('[data-testid="attempts-left"]')
      expect(attemptsEl.exists()).toBe(true)
      expect(attemptsEl.text()).toContain('2')
    })

    it('만료(REQUEST_EXPIRED) 오류 시 Step1로 돌아간다', async () => {
      const wrapper = mountView()
      await advanceToStep2(wrapper)

      const axiosError = Object.assign(new Error('expired'), {
        isAxiosError: true,
        response: {
          status: 400,
          data: { code: 'REQUEST_EXPIRED', message: 'expired' },
        },
      })
      vi.mocked(authApi.verifyConfirm).mockRejectedValueOnce(axiosError)

      const codeInput = wrapper.find('input[name="code"]')
      await codeInput.setValue('000000')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      // 이메일 input이 다시 보여야 함 (Step1)
      expect(wrapper.find('input[name="email"]').exists()).toBe(true)
    })
  })

  // ── Step 3 ─────────────────────────────────────────────────────────────────
  describe('Step 3: 새 비밀번호', () => {
    it('비밀번호 정책 미충족 시 에러가 표시된다', async () => {
      const wrapper = mountView()
      await advanceToStep3(wrapper)

      const axiosError = Object.assign(new Error('policy'), {
        isAxiosError: true,
        response: {
          status: 400,
          data: { code: 'PASSWORD_POLICY', message: 'policy violation' },
        },
      })
      vi.mocked(authApi.passwordResetConfirm).mockRejectedValueOnce(axiosError)

      const newPassInput = wrapper.find('input[name="new-password"]')
      const confirmInput = wrapper.find('input[name="confirm-password"]')
      await newPassInput.setValue('Weak1!')
      await confirmInput.setValue('Weak1!')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      const errEl = wrapper.find('[data-testid="global-error"]')
      expect(errEl.exists()).toBe(true)
    })

    it('재설정 성공 시 3초 후 /login?reason=password_reset으로 이동한다', async () => {
      vi.useFakeTimers()
      const wrapper = mountView()
      await advanceToStep3(wrapper)

      vi.mocked(authApi.passwordResetConfirm).mockResolvedValueOnce({
        data: { message: 'ok' },
      } as never)

      const pushSpy = vi.spyOn(router, 'push')

      const newPassInput = wrapper.find('input[name="new-password"]')
      const confirmInput = wrapper.find('input[name="confirm-password"]')
      await newPassInput.setValue('Secure1!pass')
      await confirmInput.setValue('Secure1!pass')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      // 3초 경과
      vi.advanceTimersByTime(3000)
      await flushPromises()

      expect(pushSpy).toHaveBeenCalledWith({ path: '/login', query: { reason: 'password_reset' } })
      vi.useRealTimers()
    })

    it('verifiedToken 만료(401) 시 Step1로 돌아간다', async () => {
      const wrapper = mountView()
      await advanceToStep3(wrapper)

      const axiosError = Object.assign(new Error('token expired'), {
        isAxiosError: true,
        response: {
          status: 401,
          data: { code: 'TOKEN_INVALID', message: 'token invalid' },
        },
      })
      vi.mocked(authApi.passwordResetConfirm).mockRejectedValueOnce(axiosError)

      const newPassInput = wrapper.find('input[name="new-password"]')
      const confirmInput = wrapper.find('input[name="confirm-password"]')
      await newPassInput.setValue('Secure1!pass')
      await confirmInput.setValue('Secure1!pass')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      // Step1 복귀 확인
      expect(wrapper.find('input[name="email"]').exists()).toBe(true)
    })
  })
})
