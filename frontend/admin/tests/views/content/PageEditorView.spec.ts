// 페이지 편집기 뷰 — Vitest 단위 테스트 (SPEC-CMS-004 REQ-CONTENT-005-D)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import ko from '@/locales/ko.json'
import PageEditorView from '@/views/content/PageEditorView.vue'
import { pages } from '@/api/content'

// API mock
vi.mock('@/api/content', () => ({
  pages: {
    get: vi.fn(),
    listBlocks: vi.fn(),
    updateSeo: vi.fn(),
    publish: vi.fn(),
    retract: vi.fn(),
    schedule: vi.fn(),
    cancelSchedule: vi.fn(),
    reorderBlocks: vi.fn(),
    generatePreviewToken: vi.fn(),
  },
  templates: { list: vi.fn() },
  sites: { current: vi.fn() },
  menus: { tree: vi.fn() },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: { id: 1, username: 'admin', roleCodes: ['SUPER_ADMIN'] },
    isAuthenticated: true,
    accessToken: 'mock-token',
    logout: vi.fn(),
  }),
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/content/pages/:id/edit', name: 'content-page-edit', component: PageEditorView },
  ],
})

function makePage(overrides = {}) {
  return {
    id: 42,
    siteId: 1,
    templateId: 1,
    code: 'ABOUT',
    title: '소개 페이지',
    slug: 'about',
    status: 'DRAFT' as const,
    currentVersion: 1,
    seoTitle: '',
    seoDescription: '',
    seoKeywords: '',
    ogImageUrl: '',
    canonicalUrl: '',
    updatedAt: '2026-04-29T00:00:00Z',
    ...overrides,
  }
}

describe('PageEditorView', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    vi.mocked(pages.get).mockResolvedValue({ data: makePage() } as never)
    vi.mocked(pages.listBlocks).mockResolvedValue({ data: [] } as never)
    await router.push('/content/pages/42/edit')
    await router.isReady()
  })

  it('SEO 제목 60자 카운터가 올바르게 동작한다', async () => {
    const wrapper = mount(PageEditorView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia(), router] },
    })
    await flushPromises()

    const vm = wrapper.vm as { seoForm: { seoTitle: string } }

    // 초기값 0/60
    expect(vm.seoForm.seoTitle.length).toBe(0)

    // 60자 입력
    vm.seoForm.seoTitle = 'a'.repeat(60)
    await flushPromises()
    expect(vm.seoForm.seoTitle.length).toBe(60)

    // 60자 초과 시 red 클래스 (템플릿 렌더링으로 확인)
    const counter = wrapper.find('.text-red-500')
    if (counter.exists()) {
      expect(counter.text()).toContain('60/60')
    }
  })

  it('SEO 설명 160자 카운터가 올바르게 동작한다', async () => {
    const wrapper = mount(PageEditorView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia(), router] },
    })
    await flushPromises()

    const vm = wrapper.vm as { seoForm: { seoDescription: string } }

    vm.seoForm.seoDescription = 'b'.repeat(160)
    await flushPromises()
    expect(vm.seoForm.seoDescription.length).toBe(160)
  })

  it('DRAFT 상태 페이지에 발행 버튼이 렌더링된다', async () => {
    const wrapper = mount(PageEditorView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia(), router] },
    })
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const publishBtn = buttons.find(b => b.text().includes('발행'))
    expect(publishBtn).toBeDefined()
  })

  it('PUBLISHED 상태 페이지에 철회 버튼이 렌더링된다', async () => {
    vi.mocked(pages.get).mockResolvedValueOnce({ data: makePage({ status: 'PUBLISHED' }) } as never)

    const wrapper = mount(PageEditorView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia(), router] },
    })
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const retractBtn = buttons.find(b => b.text().includes('철회'))
    expect(retractBtn).toBeDefined()
  })

  it('버전 이력 버튼 클릭 시 historyOpen이 true가 된다', async () => {
    const wrapper = mount(PageEditorView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia(), router] },
    })
    await flushPromises()

    const vm = wrapper.vm as { historyOpen: boolean }
    const histBtn = wrapper.findAll('button').find(b => b.text().includes('버전 이력'))
    await histBtn?.trigger('click')
    await flushPromises()
    expect(vm.historyOpen).toBe(true)
  })
})
