// SPEC-CMS-PUBLIC-001 T-008 — HomeView 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { PostSummary } from '@iroum/shared/types/api'

const noticeListMock = vi.fn()
const policyListMock = vi.fn()
const kpiValuesMock = vi.fn()

vi.mock('@/api/noticeApi', () => ({
  noticeApi: {
    list: (...args: unknown[]) => noticeListMock(...args),
    detail: vi.fn(),
  },
}))
vi.mock('@/api/policyApi', () => ({
  policyApi: {
    list: (...args: unknown[]) => policyListMock(...args),
    detail: vi.fn(),
    match: vi.fn(),
  },
}))
vi.mock('@/api/statsApi', () => ({
  statsApi: {
    kpiValues: (...args: unknown[]) => kpiValuesMock(...args),
    widget: vi.fn(),
  },
}))

function makePost(id: number): PostSummary {
  return {
    id,
    bbsId: 1,
    title: `공지 ${id}`,
    authorUsername: 'admin',
    viewCount: 1,
    likeCount: 0,
    status: 'PUBLISHED',
    isNotice: false,
    publishedAt: '2026-04-15T09:00:00Z',
    createdAt: '2026-04-15T09:00:00Z',
  }
}

async function mountView(initialPath = '/') {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
      { path: '/notices', name: 'notice-list', component: { template: '<div />' } },
      { path: '/notices/:id', name: 'notice-detail', component: { template: '<div />' } },
      { path: '/policies', name: 'policy-list', component: { template: '<div />' } },
      { path: '/policies/:id', name: 'policy-detail', component: { template: '<div />' } },
      { path: '/policies/match', name: 'policy-match', component: { template: '<div />' } },
      { path: '/faqs', name: 'faq', component: { template: '<div />' } },
      { path: '/qnas', name: 'qna-list', component: { template: '<div />' } },
      { path: '/safety/guidelines', name: 'safety-guideline-list', component: { template: '<div />' } },
      { path: '/stats', name: 'public-stats', component: { template: '<div />' } },
      { path: '/search', name: 'search', component: { template: '<div />' } },
    ],
  })
  router.push(initialPath)
  await router.isReady()
  const HomeView = (await import('@/views/HomeView.vue')).default
  const wrapper = mount(HomeView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('HomeView — Hero + 5 섹션', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    noticeListMock.mockReset()
    policyListMock.mockReset()
    kpiValuesMock.mockReset()
    localStorage.clear()
    noticeListMock.mockResolvedValue({
      content: [makePost(1), makePost(2), makePost(3), makePost(4)],
      page: 0,
      size: 4,
      totalElements: 4,
      totalPages: 1,
    })
    policyListMock.mockResolvedValue({
      content: [
        { id: 1, title: '자금지원 A', industry: 'IT', region: '서울', type: '자금지원' },
        { id: 2, title: '컨설팅 B', industry: 'IT', region: '서울', type: '컨설팅' },
        { id: 3, title: '교육 C', industry: 'IT', region: '서울', type: '교육' },
      ],
      page: 0,
      size: 3,
      totalElements: 3,
      totalPages: 1,
    })
    kpiValuesMock.mockResolvedValue([
      { code: 'visitors', label: '방문자', value: 1000 },
      { code: 'policies', label: '정책 수', value: 50 },
    ])
  })

  it('Hero 영역과 검색 폼이 렌더링된다', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="home-hero"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-search-form"]').exists()).toBe(true)
  })

  it('마운트 시 noticeApi.list({page:0, size:4}) 호출', async () => {
    await mountView()
    expect(noticeListMock).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 4 }))
  })

  it('마운트 시 policyApi.list({page:0, size:3}) 호출', async () => {
    await mountView()
    expect(policyListMock).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 3 }))
  })

  it('마운트 시 statsApi.kpiValues() 호출', async () => {
    await mountView()
    expect(kpiValuesMock).toHaveBeenCalled()
  })

  it('5 개 섹션이 모두 렌더링된다 (Hero/Notices/Policies/QuickLinks/Stats)', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="home-hero"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-notices-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-policies-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-quicklinks-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-stats-section"]').exists()).toBe(true)
  })

  it('빠른 링크 4 개 (faq/policy-match/safety/qna) 가 렌더링된다', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="home-quicklink-faq"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-quicklink-policy-match"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-quicklink-safety"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-quicklink-qna"]').exists()).toBe(true)
  })

  it('Hero 검색 제출 시 /search?q=... 로 이동', async () => {
    const { wrapper, router } = await mountView()
    await wrapper.find('[data-testid="home-search-input"]').setValue('안전')
    await wrapper.find('[data-testid="home-search-form"]').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('search')
    expect(router.currentRoute.value.query.q).toBe('안전')
  })

  // F-07: Promise.allSettled — 한 섹션이 실패해도 다른 섹션은 정상 렌더링
  it('정책 API 실패 시 정책 섹션에 ErrorState 가 표시되고 공지 섹션은 정상 렌더링된다', async () => {
    policyListMock.mockRejectedValue(new Error('policy api 500'))
    // notice 와 kpi 는 기본 success 응답 유지
    const { wrapper } = await mountView()
    // 공지 섹션: 정상 목록
    expect(wrapper.find('[data-testid="home-notices-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-notices-error"]').exists()).toBe(false)
    // 정책 섹션: ErrorState 노출
    expect(wrapper.find('[data-testid="home-policies-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-policies-list"]').exists()).toBe(false)
  })

  it('공지 API 실패 시 공지 섹션에 ErrorState 가 표시되고 정책 섹션은 정상 렌더링된다', async () => {
    noticeListMock.mockRejectedValue(new Error('notice api 500'))
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="home-notices-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-policies-list"]').exists()).toBe(true)
  })

  it('KPI 섹션은 최대 4 개까지만 표시된다', async () => {
    kpiValuesMock.mockResolvedValue([
      { code: 'v1', label: '1', value: 1 },
      { code: 'v2', label: '2', value: 2 },
      { code: 'v3', label: '3', value: 3 },
      { code: 'v4', label: '4', value: 4 },
      { code: 'v5', label: '5', value: 5 },
    ])
    const { wrapper } = await mountView()
    // home-kpi-list 와 같이 list 컨테이너는 제외하고 실제 KPI 카드만 카운트
    const cards = wrapper
      .findAll('[data-testid^="home-kpi-"]')
      .filter((el) => el.attributes('data-testid') !== 'home-kpi-list')
    expect(cards.length).toBe(4)
  })
})
