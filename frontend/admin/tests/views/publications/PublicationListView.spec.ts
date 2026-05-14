// PublicationListView 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/publication', () => ({
  listPublications: vi.fn().mockResolvedValue({
    data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
  }),
  getPublication: vi.fn(),
  getCategories: vi.fn().mockResolvedValue({ data: [] }),
  createPublication: vi.fn(),
  updatePublication: vi.fn(),
  deletePublication: vi.fn(),
  requestZipDownload: vi.fn(),
}))

import PublicationListView from '@/views/board/PublicationListView.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      publication: {
        title: '간행물 관리',
        add: '간행물 추가',
        empty: '등록된 간행물이 없습니다',
        field: { year: '발행년', month: '발행월', category: '카테고리' },
      },
      common: { search: '검색' },
    },
  },
})

function mountView() {
  return mount(PublicationListView, {
    global: { plugins: [i18n, createTestingPinia({ createSpy: vi.fn })] },
  })
}

describe('PublicationListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('간행물 관리 제목을 렌더링한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('간행물 관리')
  })

  it('검색 입력과 발행년/월 필터를 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('발행년')
    expect(wrapper.text()).toContain('발행월')
  })
})
