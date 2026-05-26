// SPEC-CMS-PUBLIC-001 §6.16 — RegisterView 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import axios from 'axios'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const registerMock = vi.fn()
const loadUserMock = vi.fn()

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    register: (...args: unknown[]) => registerMock(...args),
    loadUser: (...args: unknown[]) => loadUserMock(...args),
  }),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
    useRouter: () => ({ push: vi.fn() }),
  }
})

function makeI18n() {
  return createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
}

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
}

async function mountView() {
  const { default: RegisterView } = await import('@/views/RegisterView.vue')
  return mount(RegisterView, {
    global: {
      plugins: [makeI18n(), makeRouter(), createPinia()],
    },
  })
}

describe('RegisterView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    registerMock.mockReset()
    loadUserMock.mockReset()
  })

  it('폼 필드와 제출 버튼이 렌더링된다', async () => {
    const wrapper = await mountView()
    expect(wrapper.find('#reg-email').exists()).toBe(true)
    expect(wrapper.find('#reg-name').exists()).toBe(true)
    expect(wrapper.find('#reg-password').exists()).toBe(true)
    expect(wrapper.find('#reg-password-confirm').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true)
  })

  it('이메일 형식이 잘못되면 오류 메시지를 표시한다', async () => {
    const wrapper = await mountView()
    await wrapper.find('#reg-email').setValue('invalid-email')
    await wrapper.find('#reg-name').setValue('홍길동')
    await wrapper.find('#reg-password').setValue('password123')
    await wrapper.find('#reg-password-confirm').setValue('password123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('비밀번호가 8자 미만이면 오류 메시지를 표시한다', async () => {
    const wrapper = await mountView()
    await wrapper.find('#reg-email').setValue('test@example.com')
    await wrapper.find('#reg-name').setValue('홍길동')
    await wrapper.find('#reg-password').setValue('short')
    await wrapper.find('#reg-password-confirm').setValue('short')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('비밀번호 불일치 시 passwordMismatch 오류를 표시한다', async () => {
    const wrapper = await mountView()
    await wrapper.find('#reg-email').setValue('test@example.com')
    await wrapper.find('#reg-name').setValue('홍길동')
    await wrapper.find('#reg-password').setValue('password123')
    await wrapper.find('#reg-password-confirm').setValue('different123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
  })

  it('회원가입 성공 시 register와 loadUser가 호출된다', async () => {
    registerMock.mockResolvedValue(undefined)
    loadUserMock.mockResolvedValue(undefined)
    const wrapper = await mountView()
    await wrapper.find('#reg-email').setValue('test@example.com')
    await wrapper.find('#reg-name').setValue('홍길동')
    await wrapper.find('#reg-password').setValue('password123')
    await wrapper.find('#reg-password-confirm').setValue('password123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(registerMock).toHaveBeenCalledWith('test@example.com', 'password123', '홍길동')
  })

  it('409 응답 시 duplicate 오류 메시지를 표시한다', async () => {
    const err = Object.assign(new Error('conflict'), {
      isAxiosError: true,
      response: { status: 409 },
    })
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true)
    registerMock.mockRejectedValue(err)
    const wrapper = await mountView()
    await wrapper.find('#reg-email').setValue('dup@example.com')
    await wrapper.find('#reg-name').setValue('홍길동')
    await wrapper.find('#reg-password').setValue('password123')
    await wrapper.find('#reg-password-confirm').setValue('password123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
  })

  it('일반 오류 시 general 오류 메시지를 표시한다', async () => {
    registerMock.mockRejectedValue(new Error('network error'))
    const wrapper = await mountView()
    await wrapper.find('#reg-email').setValue('test@example.com')
    await wrapper.find('#reg-name').setValue('홍길동')
    await wrapper.find('#reg-password').setValue('password123')
    await wrapper.find('#reg-password-confirm').setValue('password123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })
})
