// SPEC-CMS-PUBLIC-001 T-008 — 검색어 히스토리 (D-04)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

vi.mock('@/api/menuApi', () => ({
  menuApi: {
    getPublicMenus: vi.fn().mockResolvedValue([]),
  },
}))

const HISTORY_KEY = 'public.search.history'

async function mountHeader() {
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
    ],
  })
  router.push('/')
  await router.isReady()
  const PublicHeader = (await import('@/components/layout/PublicHeader.vue')).default
  const wrapper = mount(PublicHeader, {
    global: { plugins: [i18n, router] },
    attachTo: document.body,
  })
  await flushPromises()
  return { wrapper, router }
}

describe('PublicHeader 검색 히스토리 — D-04', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('검색 제출 시 LocalStorage 에 키워드 저장 + /search 로 이동', async () => {
    const { wrapper, router } = await mountHeader()
    const input = wrapper.find('[data-testid="header-search-input"]')
    await input.setValue('안전')
    await wrapper.find('[data-testid="header-search-form"]').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('search')
    expect(router.currentRoute.value.query.q).toBe('안전')

    const stored = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]')
    expect(stored).toContain('안전')
    wrapper.unmount()
  })

  it('히스토리는 최대 5 개까지 저장된다 (dedup, 최근순)', async () => {
    const { wrapper } = await mountHeader()
    const form = wrapper.find('[data-testid="header-search-form"]')
    const input = wrapper.find('[data-testid="header-search-input"]')
    for (const q of ['q1', 'q2', 'q3', 'q4', 'q5', 'q6']) {
      await input.setValue(q)
      await form.trigger('submit')
      await flushPromises()
    }
    const stored = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]')
    expect(stored.length).toBe(5)
    expect(stored[0]).toBe('q6') // 가장 최근 항목이 맨 앞
    expect(stored).not.toContain('q1') // 가장 오래된 것은 제거
    wrapper.unmount()
  })

  it('동일 키워드는 dedup 되어 맨 앞으로 이동한다', async () => {
    const { wrapper } = await mountHeader()
    const form = wrapper.find('[data-testid="header-search-form"]')
    const input = wrapper.find('[data-testid="header-search-input"]')
    for (const q of ['a', 'b', 'a']) {
      await input.setValue(q)
      await form.trigger('submit')
      await flushPromises()
    }
    const stored = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]')
    expect(stored).toEqual(['a', 'b'])
    wrapper.unmount()
  })

  it('focus + 빈 입력 시 히스토리 드롭다운 표시', async () => {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(['최근1', '최근2']))
    const { wrapper } = await mountHeader()
    const input = wrapper.find('[data-testid="header-search-input"]')
    await input.trigger('focus')
    await flushPromises()
    expect(wrapper.find('[data-testid="search-history-dropdown"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('최근1')
    expect(wrapper.text()).toContain('최근2')
    wrapper.unmount()
  })

  it('히스토리 항목 X 버튼 → 해당 항목 삭제', async () => {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(['삭제대상', '유지']))
    const { wrapper } = await mountHeader()
    const input = wrapper.find('[data-testid="header-search-input"]')
    await input.trigger('focus')
    await flushPromises()
    await wrapper.find('[data-testid="search-history-remove-0"]').trigger('click')
    await flushPromises()
    const stored = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]')
    expect(stored).not.toContain('삭제대상')
    expect(stored).toContain('유지')
    wrapper.unmount()
  })

  it('"전체 삭제" 버튼 → 모든 히스토리 클리어', async () => {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(['a', 'b', 'c']))
    const { wrapper } = await mountHeader()
    const input = wrapper.find('[data-testid="header-search-input"]')
    await input.trigger('focus')
    await flushPromises()
    await wrapper.find('[data-testid="search-history-clear-all"]').trigger('click')
    await flushPromises()
    const stored = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]')
    expect(stored).toEqual([])
    wrapper.unmount()
  })

  it('히스토리 항목 클릭 시 해당 키워드로 검색', async () => {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(['저장된 검색어']))
    const { wrapper, router } = await mountHeader()
    const input = wrapper.find('[data-testid="header-search-input"]')
    await input.trigger('focus')
    await flushPromises()
    await wrapper.find('[data-testid="search-history-item-0"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('search')
    expect(router.currentRoute.value.query.q).toBe('저장된 검색어')
    wrapper.unmount()
  })
})
