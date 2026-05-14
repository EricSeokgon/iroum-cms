// SPEC-CMS-PUBLIC-001 T-010 — A11y axe 검증 (E-01 ~ E-04)
// HomeView / PublicHeader / PolicyMatchView / QnaCreateView
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import { axe } from 'jest-axe'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

// 모든 API mock — 네트워크 차단
vi.mock('@/api/noticeApi', () => ({
  noticeApi: {
    list: vi.fn().mockResolvedValue({
      content: [],
      page: 0,
      size: 4,
      totalElements: 0,
      totalPages: 0,
    }),
    detail: vi.fn(),
  },
}))
vi.mock('@/api/policyApi', () => ({
  policyApi: {
    list: vi.fn().mockResolvedValue({
      content: [],
      page: 0,
      size: 3,
      totalElements: 0,
      totalPages: 0,
    }),
    match: vi.fn().mockResolvedValue([]),
    detail: vi.fn(),
  },
}))
vi.mock('@/api/statsApi', () => ({
  statsApi: {
    kpiValues: vi.fn().mockResolvedValue([]),
    widget: vi.fn(),
  },
}))
vi.mock('@/api/menuApi', () => ({
  menuApi: {
    getPublicMenus: vi.fn().mockResolvedValue([
      { id: 1, code: 'NOTICE', name: '공지', path: '/notices', parentId: null, depth: 1, sortOrder: 1 },
    ]),
  },
}))
vi.mock('@/api/qnaApi', () => ({
  qnaApi: {
    create: vi.fn(),
    list: vi.fn(),
    detail: vi.fn(),
  },
}))
// element-plus 의 컴포넌트는 stub, ElMessage 만 vi.fn() 으로 mock
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElSkeleton: {
    name: 'ElSkeleton',
    props: ['rows', 'animated'],
    template: '<div class="el-skeleton-stub" aria-hidden="true" />',
  },
}))

function makeHarness() {
  setActivePinia(createPinia())
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
      { path: '/notices', name: 'notice-list', component: { template: '<div />' } },
      { path: '/notices/:id', name: 'notice-detail', component: { template: '<div />' } },
      { path: '/policies', name: 'policy-list', component: { template: '<div />' } },
      { path: '/policies/:id', name: 'policy-detail', component: { template: '<div />' } },
      { path: '/policies/match', name: 'policy-match', component: { template: '<div />' } },
      { path: '/faqs', name: 'faq', component: { template: '<div />' } },
      { path: '/qnas', name: 'qna-list', component: { template: '<div />' } },
      { path: '/qnas/new', name: 'qna-create', component: { template: '<div />' } },
      { path: '/qnas/:id', name: 'qna-detail', component: { template: '<div />' } },
      { path: '/safety/guidelines', name: 'safety-guideline-list', component: { template: '<div />' } },
      { path: '/stats', name: 'public-stats', component: { template: '<div />' } },
      { path: '/search', name: 'search', component: { template: '<div />' } },
    ],
  })
  return { i18n, router }
}

// jest-axe 규칙: critical 위반만 검증 (jsdom 한정 color-contrast 불안정 회피)
const axeOptions = {
  rules: {
    'color-contrast': { enabled: false }, // jsdom: 실제 색상 계산 불가
    region: { enabled: false }, // 컴포넌트 단위 mount → landmark 부재 무시
  },
}

describe('E-01 / E-02 / E-03 — HomeView axe', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('HomeView 마운트 후 axe 위반 0건', async () => {
    const { i18n, router } = makeHarness()
    router.push('/')
    await router.isReady()
    const HomeView = (await import('@/views/HomeView.vue')).default
    const wrapper = mount(HomeView, {
      global: { plugins: [i18n, router] },
      attachTo: document.body,
    })
    await flushPromises()
    const results = await axe(wrapper.element as HTMLElement, axeOptions)
    expect(results).toHaveNoViolations()
    wrapper.unmount()
  })
})

describe('E-02 — PublicHeader axe (아이콘 버튼 aria-label)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('PublicHeader 의 햄버거/언어토글 버튼이 aria-label 을 갖고 axe 위반 0건', async () => {
    const { i18n, router } = makeHarness()
    router.push('/')
    await router.isReady()
    const PublicHeader = (await import('@/components/layout/PublicHeader.vue')).default
    const wrapper = mount(PublicHeader, {
      global: { plugins: [i18n, router] },
      attachTo: document.body,
    })
    await flushPromises()

    // 햄버거 / 언어 토글 모두 aria-label 보유
    const buttons = wrapper.findAll('button')
    buttons.forEach((btn) => {
      // aria-label 또는 텍스트 컨텐츠 둘 중 하나는 반드시 존재
      const hasLabel = !!btn.attributes('aria-label') || btn.text().trim().length > 0
      expect(hasLabel).toBe(true)
    })

    const results = await axe(wrapper.element as HTMLElement, axeOptions)
    expect(results).toHaveNoViolations()
    wrapper.unmount()
  })
})

describe('E-08 — PublicHeader 모바일 반응형 클래스', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('데스크탑 nav 는 md 이상에서만 표시 (.hidden.md:block)', async () => {
    const { i18n, router } = makeHarness()
    router.push('/')
    await router.isReady()
    const PublicHeader = (await import('@/components/layout/PublicHeader.vue')).default
    const wrapper = mount(PublicHeader, { global: { plugins: [i18n, router] } })
    await flushPromises()
    // 데스크탑 nav 는 hidden md:block — 모바일에서 hidden
    const desktopNav = wrapper.find('nav.hidden.md\\:block')
    expect(desktopNav.exists()).toBe(true)
  })

  it('햄버거 버튼은 md 이하(모바일)에서만 표시 (.md:hidden)', async () => {
    const { i18n, router } = makeHarness()
    router.push('/')
    await router.isReady()
    const PublicHeader = (await import('@/components/layout/PublicHeader.vue')).default
    const wrapper = mount(PublicHeader, { global: { plugins: [i18n, router] } })
    await flushPromises()
    const hamburger = wrapper.find('button[aria-expanded]')
    expect(hamburger.exists()).toBe(true)
    expect(hamburger.classes()).toContain('md:hidden')
  })
})

describe('E-04 — PolicyMatchView axe (폼 라벨 연결)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('PolicyMatchView 마운트 후 axe 위반 0건', async () => {
    const { i18n, router } = makeHarness()
    router.push('/policies/match')
    await router.isReady()
    const PolicyMatchView = (await import('@/views/policies/PolicyMatchView.vue')).default
    const wrapper = mount(PolicyMatchView, {
      global: { plugins: [i18n, router] },
      attachTo: document.body,
    })
    await flushPromises()
    const results = await axe(wrapper.element as HTMLElement, axeOptions)
    expect(results).toHaveNoViolations()
    wrapper.unmount()
  })
})

describe('E-04 — QnaCreateView axe (폼 라벨 + aria-describedby)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('QnaCreateView 마운트 후 axe 위반 0건', async () => {
    const { i18n, router } = makeHarness()
    router.push('/qnas/new')
    await router.isReady()
    const QnaCreateView = (await import('@/views/qnas/QnaCreateView.vue')).default
    const wrapper = mount(QnaCreateView, {
      global: { plugins: [i18n, router] },
      attachTo: document.body,
    })
    await flushPromises()
    const results = await axe(wrapper.element as HTMLElement, axeOptions)
    expect(results).toHaveNoViolations()
    wrapper.unmount()
  })

  it('필수 필드(title, content)에 label + aria-required 가 연결된다', async () => {
    const { i18n, router } = makeHarness()
    router.push('/qnas/new')
    await router.isReady()
    const QnaCreateView = (await import('@/views/qnas/QnaCreateView.vue')).default
    const wrapper = mount(QnaCreateView, {
      global: { plugins: [i18n, router] },
    })
    await flushPromises()

    // label for=id 연결 검증
    const titleInput = wrapper.find('#qna-title')
    const contentInput = wrapper.find('#qna-content')
    expect(titleInput.exists()).toBe(true)
    expect(contentInput.exists()).toBe(true)
    // required 속성 (또는 aria-required="true")
    expect(titleInput.attributes('required')).toBeDefined()
    expect(contentInput.attributes('required')).toBeDefined()
    // 라벨 존재
    expect(wrapper.find('label[for="qna-title"]').exists()).toBe(true)
    expect(wrapper.find('label[for="qna-content"]').exists()).toBe(true)
  })
})
