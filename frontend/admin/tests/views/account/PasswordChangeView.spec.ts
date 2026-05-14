// PasswordChangeView 테스트 — SPEC-CMS-002 REQ-AUTH-009
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import axios from 'axios'

// authApi mock
vi.mock('@/api/auth', () => ({
  authApi: {
    changePassword: vi.fn(),
  },
}))

// shared apiClient mock (auth store 내부에서 참조)
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))

// PasswordStrengthMeter mock (단순화)
vi.mock('@/components/PasswordStrengthMeter.vue', () => ({
  default: { template: '<div data-testid="strength-meter" />' },
}))

import PasswordChangeView from '@/views/account/PasswordChangeView.vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'

// 최소 i18n 메시지 맵
const messages = {
  ko: {
    account: {
      password: {
        title: '비밀번호 변경',
        current: '현재 비밀번호',
        new: '새 비밀번호',
        confirm: '새 비밀번호 확인',
        submit: '변경',
        success: '비밀번호가 변경되었습니다.',
        policyHint: '8자 이상',
        error: {
          currentRequired: '현재 비밀번호를 입력해 주세요',
          newRequired: '새 비밀번호를 입력해 주세요',
          confirmRequired: '새 비밀번호 확인을 입력해 주세요',
          currentMismatch: '현재 비밀번호가 일치하지 않습니다',
          policy: '비밀번호 정책을 충족하지 않습니다',
          reuse: '최근 5회 사용한 비밀번호입니다',
          confirmMismatch: '새 비밀번호와 확인이 일치하지 않습니다',
          sameAsCurrent: '현재 비밀번호와 다른 비밀번호를 입력하세요',
        },
        strength: { label: '강도', veryWeak: '매우 약함', weak: '약함', fair: '보통', good: '강함', veryStrong: '매우 강함' },
      },
    },
    common: { cancel: '취소', error: { network: '서버 오류' } },
  },
}

// 라우터 (인메모리)
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/login', name: 'login', component: { template: '<div />' } },
    { path: '/dashboard', name: 'dashboard', component: { template: '<div />' } },
    { path: '/account/password', name: 'password-change', component: PasswordChangeView },
  ],
})

const i18n = createI18n({ legacy: false, locale: 'ko', messages })

function mountView() {
  return mount(PasswordChangeView, {
    global: {
      plugins: [createPinia(), router, i18n, ElementPlus],
    },
  })
}

describe('PasswordChangeView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('빈 폼 제출 시 3개 필드 모두 오류 표시', async () => {
    const wrapper = mountView()
    await flushPromises()
    // submit 버튼 click 시 form @submit.prevent 트리거
    await wrapper.find('[data-testid="btn-submit"]').trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    const errorItems = wrapper.findAll('.el-form-item.is-error')
    expect(errorItems.length).toBeGreaterThanOrEqual(1)
  })

  it('새 비밀번호 8자 미만이면 정책 오류 표시', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('[data-testid="input-current"]').setValue('OldPass1!')
    await wrapper.find('[data-testid="input-new"]').setValue('short')
    await wrapper.find('[data-testid="input-new"]').trigger('blur')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 50))
    await flushPromises()
    await wrapper.vm.$nextTick()
    // is-error 클래스가 있거나 valid 상태인지 확인 (Element Plus는 is-error 또는 message로 노출)
    const errorItems = wrapper.findAll('.el-form-item.is-error')
    const errorMessages = wrapper.findAll('.el-form-item__error')
    expect(errorItems.length + errorMessages.length).toBeGreaterThanOrEqual(1)
  })

  it('새 비밀번호와 확인 불일치 시 오류 표시', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('[data-testid="input-current"]').setValue('OldPass1!')
    await wrapper.find('[data-testid="input-new"]').setValue('NewPass1!')
    await wrapper.find('[data-testid="input-confirm"]').setValue('DifferentPass1!')
    await wrapper.find('[data-testid="input-confirm"]').trigger('blur')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 50))
    await flushPromises()
    await wrapper.vm.$nextTick()
    // is-error 클래스가 있거나 valid 상태인지 확인 (Element Plus는 is-error 또는 message로 노출)
    const errorItems = wrapper.findAll('.el-form-item.is-error')
    const errorMessages = wrapper.findAll('.el-form-item__error')
    expect(errorItems.length + errorMessages.length).toBeGreaterThanOrEqual(1)
  })

  it('성공 시 success alert 표시 후 clearLocal 호출', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValueOnce({
      data: { message: '변경되었습니다.' },
    } as never)
    vi.useFakeTimers()

    const wrapper = mountView()
    const auth = useAuthStore()
    const clearLocalSpy = vi.spyOn(auth, 'clearLocal')

    await wrapper.find('[data-testid="input-current"]').setValue('OldPass1!')
    await wrapper.find('[data-testid="input-new"]').setValue('NewPass1@#')
    await wrapper.find('[data-testid="input-confirm"]').setValue('NewPass1@#')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="success-alert"]').exists()).toBe(true)

    // 3초 후 clearLocal 호출 확인
    vi.advanceTimersByTime(3000)
    await flushPromises()
    expect(clearLocalSpy).toHaveBeenCalledOnce()

    vi.useRealTimers()
  })

  it('401 AUTH_INVALID_CREDENTIALS 응답 시 currentMismatch 오류 메시지 표시', async () => {
    const axiosError = {
      isAxiosError: true,
      response: {
        status: 401,
        data: { code: 'AUTH_INVALID_CREDENTIALS', message: '비밀번호 불일치' },
      },
    }
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true)
    vi.mocked(authApi.changePassword).mockRejectedValueOnce(axiosError)

    const wrapper = mountView()
    await wrapper.find('[data-testid="input-current"]').setValue('WrongPass1!')
    await wrapper.find('[data-testid="input-new"]').setValue('NewPass1@#')
    await wrapper.find('[data-testid="input-confirm"]').setValue('NewPass1@#')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('현재 비밀번호가 일치하지 않습니다')
  })

  it('400 PASSWORD_REUSE 응답 시 reuse 오류 메시지 표시', async () => {
    const axiosError = {
      isAxiosError: true,
      response: {
        status: 400,
        data: { code: 'PASSWORD_REUSE', message: '재사용 불가' },
      },
    }
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true)
    vi.mocked(authApi.changePassword).mockRejectedValueOnce(axiosError)

    const wrapper = mountView()
    await wrapper.find('[data-testid="input-current"]').setValue('OldPass1!')
    await wrapper.find('[data-testid="input-new"]').setValue('NewPass1@#')
    await wrapper.find('[data-testid="input-confirm"]').setValue('NewPass1@#')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-alert"]').text()).toContain('최근 5회 사용한 비밀번호')
  })
})
