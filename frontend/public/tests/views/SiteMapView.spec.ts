// SPEC-CMS-PUBLIC-001 T-008 — SiteMapView 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

vi.mock('@/api/menuApi', () => ({
  menuApi: {
    getPublicMenus: vi.fn().mockResolvedValue([
      {
        id: 1,
        code: 'NOTICE',
        name: '공지사항',
        path: '/notices',
        parentId: null,
        depth: 1,
        sortOrder: 1,
        children: [
          { id: 11, code: 'NOTICE_GENERAL', name: '일반공지', path: '/notices?cat=GENERAL', parentId: 1, depth: 2, sortOrder: 1 },
          { id: 12, code: 'NOTICE_EVENT', name: '행사공지', path: '/notices?cat=EVENT', parentId: 1, depth: 2, sortOrder: 2 },
        ],
      },
      {
        id: 2,
        code: 'POLICY',
        name: '정책사업',
        path: '/policies',
        parentId: null,
        depth: 1,
        sortOrder: 2,
        children: [
          {
            id: 21,
            code: 'POLICY_MATCH',
            name: '정책 매칭',
            path: '/policies/match',
            parentId: 2,
            depth: 2,
            sortOrder: 1,
            children: [
              { id: 211, code: 'POLICY_SUB', name: '구독', path: '/policies/subscriptions', parentId: 21, depth: 3, sortOrder: 1 },
            ],
          },
        ],
      },
    ]),
  },
}))

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/sitemap', name: 'sitemap', component: () => import('@/views/SiteMapView.vue') }],
  })
  router.push('/sitemap')
  await router.isReady()
  const SiteMapView = (await import('@/views/SiteMapView.vue')).default
  const wrapper = mount(SiteMapView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('SiteMapView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('nav[aria-label="사이트맵"] 으로 렌더링', async () => {
    const wrapper = await mountView()
    const nav = wrapper.find('[data-testid="sitemap-nav"]')
    expect(nav.exists()).toBe(true)
    expect(nav.attributes('aria-label')).toBe('사이트맵')
  })

  it('루트 메뉴 2 개 (공지사항, 정책사업) 렌더링', async () => {
    const wrapper = await mountView()
    const root = wrapper.find('[data-testid="sitemap-root"]')
    expect(root.exists()).toBe(true)
    expect(wrapper.text()).toContain('공지사항')
    expect(wrapper.text()).toContain('정책사업')
  })

  it('2 단계 자식 메뉴가 중첩 ul 로 렌더링', async () => {
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="sitemap-children-1"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('일반공지')
    expect(wrapper.text()).toContain('행사공지')
  })

  it('3 단계 손자 메뉴까지 렌더링된다', async () => {
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="sitemap-grandchildren-21"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('구독')
  })

  it('각 메뉴 항목은 path 로 링크된다', async () => {
    const wrapper = await mountView()
    const links = wrapper.findAll('a')
    const hrefs = links.map((l) => l.attributes('href'))
    expect(hrefs).toContain('/notices')
    expect(hrefs).toContain('/policies')
    expect(hrefs).toContain('/notices?cat=GENERAL')
  })
})
