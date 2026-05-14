// SPEC-CMS-PUBLIC-001 T-005 — PublicLayout 통합 검증 (AC A-01, A-02, A-04)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import { axe } from 'jest-axe'

import PublicLayout from '@/layouts/PublicLayout.vue'
import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

vi.mock('@/api/menuApi', () => ({
  menuApi: {
    getPublicMenus: vi.fn().mockResolvedValue([
      { id: 1, code: 'NOTICE', name: '공지사항', path: '/notices', parentId: null, depth: 1, sortOrder: 1 },
      { id: 2, code: 'POLICY', name: '정책사업', path: '/policies', parentId: null, depth: 1, sortOrder: 2 },
    ]),
  },
}))
vi.mock('@/api/systemApi', () => ({
  systemApi: { health: vi.fn().mockResolvedValue({ status: 'UP', maintenanceMode: false }) },
}))

function setupHarness() {
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
      { path: '/', name: 'home', component: { template: '<div>home</div>' } },
      { path: '/notices', name: 'notice-list', component: { template: '<div>notices</div>' } },
      { path: '/sitemap', name: 'sitemap', component: { template: '<div>sitemap</div>' } },
    ],
  })
  return { i18n, router }
}

describe('PublicLayout — A-01 PublicLayout 4개 랜드마크 + 스킵 내비', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('header role="banner", main role="main", footer role="contentinfo" 모두 렌더링', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    expect(wrapper.find('header[role="banner"]').exists()).toBe(true)
    expect(wrapper.find('main[role="main"]').exists()).toBe(true)
    expect(wrapper.find('footer[role="contentinfo"]').exists()).toBe(true)
  })

  it('스킵 내비 링크가 첫 번째 포커스 가능 요소 + href="#main-content"', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    const skipNav = wrapper.find('a[href="#main-content"]')
    expect(skipNav.exists()).toBe(true)
    expect(skipNav.text()).toBe('본문 바로가기')
  })

  it('main element는 id="main-content" + tabindex="-1" (스킵 내비 타겟)', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    const main = wrapper.find('main#main-content')
    expect(main.exists()).toBe(true)
    expect(main.attributes('tabindex')).toBe('-1')
  })

  it('jest-axe — 접근성 위반 0건 (critical)', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] }, attachTo: document.body })
    await flushPromises()

    const results = await axe(wrapper.element as HTMLElement)
    expect(results).toHaveNoViolations()
    wrapper.unmount()
  })
})

describe('PublicHeader — A-02 메뉴 트리, A-05 언어 토글', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('menuStore 메뉴 항목이 nav role="navigation" 하위에 렌더링된다', async () => {
    const { i18n, router } = setupHarness()
    const { useMenuStore } = await import('@/stores/menuStore')
    const menuStore = useMenuStore()
    await menuStore.fetchMenus()

    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('공지사항')
    expect(wrapper.text()).toContain('정책사업')
    expect(wrapper.find('nav[role="navigation"]').exists()).toBe(true)
  })

  it('언어 토글 버튼은 aria-pressed 속성으로 현재 locale을 표현한다', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    const toggleBtn = wrapper.find('button[aria-pressed]')
    expect(toggleBtn.exists()).toBe(true)
    expect(toggleBtn.attributes('aria-pressed')).toBe('false') // 초기 ko → aria-pressed=false (en 아님)
  })

  it('언어 토글 클릭 시 localeStore.setLocale 호출', async () => {
    const { i18n, router } = setupHarness()
    const { useLocaleStore } = await import('@/stores/localeStore')
    const localeStore = useLocaleStore()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    const toggleBtn = wrapper.find('button[aria-pressed]')
    await toggleBtn.trigger('click')
    expect(localeStore.locale).toBe('en')
  })
})

describe('PublicHeader — A-07 모바일 햄버거', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('햄버거 버튼은 aria-label + aria-expanded 속성을 갖는다', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    const hamburger = wrapper.find('button[aria-expanded]')
    expect(hamburger.exists()).toBe(true)
    expect(hamburger.attributes('aria-expanded')).toBe('false')
    expect(hamburger.attributes('aria-label')).toBeDefined()
  })

  it('햄버거 클릭 시 aria-expanded가 true로 토글된다', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    const hamburger = wrapper.find('button[aria-expanded]')
    await hamburger.trigger('click')
    expect(hamburger.attributes('aria-expanded')).toBe('true')
  })
})

describe('PublicBreadcrumb — A-03 브레드크럼', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('breadcrumbStore.items가 있을 때 nav aria-label="현재 위치"로 ol > li 렌더링', async () => {
    const { i18n, router } = setupHarness()
    const { useBreadcrumbStore } = await import('@/stores/breadcrumbStore')
    const breadcrumb = useBreadcrumbStore()
    breadcrumb.set([
      { label: 'route.home', path: '/' },
      { label: 'route.notice.list', path: '/notices' },
    ])

    router.push('/notices')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    const nav = wrapper.find('nav[aria-label="현재 위치"]')
    expect(nav.exists()).toBe(true)
    const items = nav.findAll('li')
    expect(items.length).toBe(2)
    // 마지막 항목은 aria-current="page"
    const lastSpan = items[items.length - 1].find('[aria-current="page"]')
    expect(lastSpan.exists()).toBe(true)
  })

  it('breadcrumbStore.items가 비어 있으면 nav를 렌더링하지 않는다', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    expect(wrapper.find('nav[aria-label="현재 위치"]').exists()).toBe(false)
  })
})

describe('PublicFooter — A-06 푸터', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('footer는 role="contentinfo" + 사이트맵 링크 포함', async () => {
    const { i18n, router } = setupHarness()
    router.push('/')
    await router.isReady()
    const wrapper = mount(PublicLayout, { global: { plugins: [i18n, router] } })
    await flushPromises()

    const footer = wrapper.find('footer[role="contentinfo"]')
    expect(footer.exists()).toBe(true)
    expect(footer.text()).toContain('iroum-cms')
    expect(footer.text()).toContain('사이트맵')
  })
})
