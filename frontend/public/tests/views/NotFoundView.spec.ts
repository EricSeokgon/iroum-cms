// SPEC-CMS-PUBLIC-001 T-010 — NotFoundView (F-01) 검증
import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import NotFoundView from '@/views/NotFoundView.vue'

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
      { path: '/search', name: 'search', component: { template: '<div />' } },
      { path: '/missing', name: 'not-found', component: NotFoundView },
    ],
  })
  router.push('/missing')
  await router.isReady()
  const wrapper = mount(NotFoundView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('NotFoundView — F-01', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('h1 "페이지를 찾을 수 없습니다" 가 렌더링된다', async () => {
    const { wrapper } = await mountView()
    const heading = wrapper.find('h1')
    expect(heading.exists()).toBe(true)
    expect(heading.text()).toBe('페이지를 찾을 수 없습니다')
  })

  it('홈으로 이동 버튼이 home 라우트로 link 된다', async () => {
    const { wrapper } = await mountView()
    const homeLink = wrapper.find('[data-testid="not-found-home"]')
    expect(homeLink.exists()).toBe(true)
    // router-link href 검증
    expect(homeLink.attributes('href')).toBe('/')
  })

  it('이전 페이지 버튼이 존재한다 (router.back)', async () => {
    const { wrapper } = await mountView()
    const back = wrapper.find('[data-testid="not-found-back"]')
    expect(back.exists()).toBe(true)
    expect(back.text()).toBe('이전 페이지')
  })

  it('통합 검색 링크가 search 라우트로 연결된다', async () => {
    const { wrapper } = await mountView()
    const search = wrapper.find('[data-testid="not-found-search"]')
    expect(search.exists()).toBe(true)
    expect(search.attributes('href')).toBe('/search')
  })

  it('aria-labelledby 가 heading id 와 연결된다', async () => {
    const { wrapper } = await mountView()
    const section = wrapper.find('[data-testid="not-found-view"]')
    expect(section.attributes('aria-labelledby')).toBe('not-found-heading')
    expect(wrapper.find('#not-found-heading').exists()).toBe(true)
  })
})
