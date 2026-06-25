/**
 * PageListView 단위 테스트 — SPEC-CMS-PAGE-HISTORY-001 REQ-PHIST-005
 * (AC-PHIST-016 ~ AC-PHIST-019: 목록 화면 이력 진입점)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import PageListView from '@/views/content/PageListView.vue'
import { pages } from '@/api/content'

vi.mock('@/api/content', () => ({
  pages: {
    list: vi.fn(),
    create: vi.fn(),
    publish: vi.fn(),
    schedule: vi.fn(),
    retract: vi.fn(),
    history: vi.fn(),
    rollback: vi.fn(),
  },
  templates: { list: vi.fn() },
}))

vi.mock('@/stores/content', () => ({
  useSiteStore: () => ({
    currentSite: { id: 1 },
    loading: false,
    fetchCurrent: vi.fn().mockResolvedValue(undefined),
  }),
}))

const hasPermissionMock = vi.fn<(code: string) => boolean>(() => true)
vi.mock('@/stores/permissionStore', () => ({
  usePermissionStore: () => ({ hasPermission: hasPermissionMock }),
}))

const routerPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
}))

import ElementPlus from 'element-plus'

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko }, missingWarn: false, fallbackWarn: false })

function makePage(overrides = {}) {
  return {
    id: 1,
    siteId: 1,
    templateId: 1,
    code: 'ABOUT',
    title: '회사 소개',
    slug: 'about',
    status: 'DRAFT' as const,
    currentVersion: 1,
    updatedAt: '2026-06-25T00:00:00Z',
    ...overrides,
  }
}

function mountView() {
  return mount(PageListView, {
    global: { plugins: [ElementPlus, i18n] },
  })
}

describe('PageListView 이력 진입점 (REQ-PHIST-005)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hasPermissionMock.mockReturnValue(true)
    vi.mocked(pages.list).mockResolvedValue({
      data: { content: [makePage()], totalElements: 1 },
    } as never)
    vi.mocked(pages.history).mockResolvedValue({ data: [] } as never)
  })

  it('AC-PHIST-016: 각 행에 이력 버튼을 렌더링한다', async () => {
    const wrapper = mountView()
    await flushPromises()

    const historyBtn = wrapper.findAll('button').find((b) => b.text() === '이력')
    expect(historyBtn).toBeTruthy()
  })

  it('AC-PHIST-017: 이력 버튼 클릭 시 PageHistoryDialog가 해당 pageId로 열린다', async () => {
    const wrapper = mountView()
    await flushPromises()

    const historyBtn = wrapper.findAll('button').find((b) => b.text() === '이력')
    await historyBtn!.trigger('click')
    await flushPromises()

    const dialog = wrapper.findComponent({ name: 'PageHistoryDialog' })
    expect(dialog.exists()).toBe(true)
    expect(dialog.props('modelValue')).toBe(true)
    expect(dialog.props('pageId')).toBe(1)
  })

  it('AC-PHIST-018: 다이얼로그 rolledBack 이벤트 시 목록을 새로고침한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    vi.mocked(pages.list).mockClear()

    const dialog = wrapper.findComponent({ name: 'PageHistoryDialog' })
    dialog.vm.$emit('rolledBack')
    await flushPromises()

    expect(pages.list).toHaveBeenCalledTimes(1)
  })

  it('AC-PHIST-019: PAGE:HISTORY:READ 권한이 없으면 이력 버튼을 렌더링하지 않는다', async () => {
    hasPermissionMock.mockImplementation((code: string) => code !== 'PAGE:HISTORY:READ')
    const wrapper = mountView()
    await flushPromises()

    const historyBtn = wrapper.findAll('button').find((b) => b.text() === '이력')
    expect(historyBtn).toBeUndefined()
  })
})
