// SPEC-CMS-PUBLIC-001 T-010 — ForbiddenView (F-02) 검증
import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import ForbiddenView from '@/views/errors/ForbiddenView.vue'

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
      { path: '/error/403', name: 'forbidden', component: ForbiddenView },
    ],
  })
  router.push('/error/403')
  await router.isReady()
  const wrapper = mount(ForbiddenView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('ForbiddenView — F-02', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('h1 "권한이 없습니다" 가 렌더링된다', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.find('h1').text()).toBe('권한이 없습니다')
  })

  it('홈으로 이동 링크가 home 라우트로 연결된다', async () => {
    const { wrapper } = await mountView()
    const homeLink = wrapper.find('[data-testid="forbidden-home"]')
    expect(homeLink.exists()).toBe(true)
    expect(homeLink.attributes('href')).toBe('/')
  })

  it('aria-labelledby 가 heading id 와 연결된다', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="forbidden-view"]').attributes('aria-labelledby')).toBe(
      'forbidden-heading',
    )
  })
})
