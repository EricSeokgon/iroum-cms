// SPEC-CMS-PUBLIC-001 T-010 — ServerErrorView (F-03) 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import ServerErrorView from '@/views/errors/ServerErrorView.vue'

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/error/500', name: 'server-error', component: ServerErrorView },
    ],
  })
  router.push('/error/500')
  await router.isReady()
  const wrapper = mount(ServerErrorView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('ServerErrorView — F-03', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('h1 "일시적인 오류가 발생했습니다" 가 렌더링된다', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.find('h1').text()).toBe('일시적인 오류가 발생했습니다')
  })

  it('다시 시도 버튼이 존재하고 클릭 시 window.location.reload 가 호출된다', async () => {
    const { wrapper } = await mountView()
    const reloadSpy = vi.fn()
    // jsdom 의 window.location 은 reload 가 없는 환경이 있으므로 fallback 처리
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...window.location, reload: reloadSpy },
    })

    const retry = wrapper.find('[data-testid="server-error-retry"]')
    expect(retry.exists()).toBe(true)
    await retry.trigger('click')
    expect(reloadSpy).toHaveBeenCalled()
  })

  it('홈으로 이동 링크가 home 라우트로 연결된다', async () => {
    const { wrapper } = await mountView()
    const home = wrapper.find('[data-testid="server-error-home"]')
    expect(home.exists()).toBe(true)
    expect(home.attributes('href')).toBe('/')
  })
})
