// SPEC-CMS-PUBLIC-001 T-008 — SearchResultView 검증 (D-01/D-02/D-03)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const searchMock = vi.fn()
vi.mock('@/api/searchApi', async () => {
  const actual = await vi.importActual<typeof import('@/api/searchApi')>('@/api/searchApi')
  return {
    ...actual,
    searchApi: {
      search: (...args: unknown[]) => searchMock(...args),
    },
  }
})

async function mountView(initialPath = '/search?q=안전') {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/search',
        name: 'search',
        component: () => import('@/views/SearchResultView.vue'),
      },
    ],
  })
  router.push(initialPath)
  await router.isReady()
  const SearchResultView = (await import('@/views/SearchResultView.vue')).default
  const wrapper = mount(SearchResultView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('SearchResultView — D-01 통합 검색 결과 타입 탭', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    searchMock.mockReset()
    localStorage.clear()
  })

  it('마운트 시 searchApi.search 가 q 와 함께 호출된다', async () => {
    searchMock.mockResolvedValue({
      totalElements: 5,
      page: 0,
      size: 20,
      results: [],
      facets: { POST: 2, FAQ: 1, POLICY: 2 },
    })
    await mountView('/search?q=안전')
    expect(searchMock).toHaveBeenCalledTimes(1)
    expect(searchMock).toHaveBeenCalledWith(
      expect.objectContaining({ q: '안전', type: 'ALL', page: 0, size: 20 }),
    )
  })

  it('6 개 탭이 렌더링된다 (ALL/POST/FAQ/QNA/POLICY/SAFETY)', async () => {
    searchMock.mockResolvedValue({
      totalElements: 0,
      page: 0,
      size: 20,
      results: [],
      facets: {},
    })
    const { wrapper } = await mountView('/search?q=test')
    expect(wrapper.find('[data-testid="search-filter-tabs"]').exists()).toBe(true)
    expect(wrapper.findAll('[role="tab"]').length).toBe(6)
  })

  it('탭 클릭 시 URL 의 type 이 업데이트된다', async () => {
    searchMock.mockResolvedValue({
      totalElements: 5,
      page: 0,
      size: 20,
      results: [],
      facets: { SAFETY: 3 },
    })
    const { wrapper, router } = await mountView('/search?q=안전')
    await wrapper.find('[data-testid="search-tab-SAFETY"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.type).toBe('SAFETY')
  })

  it('URL ?type=POLICY 로 진입 시 해당 탭이 active', async () => {
    searchMock.mockResolvedValue({
      totalElements: 0,
      page: 0,
      size: 20,
      results: [],
      facets: {},
    })
    const { wrapper } = await mountView('/search?q=정책&type=POLICY')
    expect(wrapper.find('[data-testid="search-tab-POLICY"]').attributes('aria-selected')).toBe('true')
    expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'POLICY' }))
  })
})

describe('SearchResultView — D-02 mark 하이라이트', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    searchMock.mockReset()
    localStorage.clear()
  })

  it('결과 snippet 에 <mark> 태그가 보존되고 <script> 는 제거된다', async () => {
    searchMock.mockResolvedValue({
      totalElements: 1,
      page: 0,
      size: 20,
      results: [
        {
          id: 1,
          type: 'POST',
          title: '안전 공지',
          snippet: '<mark>안전</mark> 가이드 <script>alert(1)</script>',
          url: '/notices/1',
          score: 0.9,
        },
      ],
      facets: { POST: 1 },
    })
    const { wrapper } = await mountView('/search?q=안전')
    const snippet = wrapper.find('[data-testid="search-result-snippet"]')
    expect(snippet.html()).toContain('<mark>안전</mark>')
    expect(snippet.html()).not.toContain('<script>')
    expect(snippet.element.querySelectorAll('script').length).toBe(0)
  })
})

describe('SearchResultView — D-03 빈 결과 메시지', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    searchMock.mockReset()
    localStorage.clear()
  })

  it('totalElements=0 일 때 EmptyState + 검색 팁이 표시된다', async () => {
    searchMock.mockResolvedValue({
      totalElements: 0,
      page: 0,
      size: 20,
      results: [],
      facets: {},
    })
    const { wrapper } = await mountView('/search?q=없는검색어')
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-empty-tip"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('다른 검색어로 시도')
  })

  it('totalElements=0 이어도 쿼리 텍스트가 헤더에 표시된다', async () => {
    searchMock.mockResolvedValue({
      totalElements: 0,
      page: 0,
      size: 20,
      results: [],
      facets: {},
    })
    const { wrapper } = await mountView('/search?q=없는검색어')
    expect(wrapper.find('[data-testid="search-summary"]').text()).toContain('없는검색어')
  })
})
