// FaqListView 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/faq', () => ({
  listFaqs: vi.fn().mockResolvedValue({
    data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
  }),
  getCategories: vi.fn().mockResolvedValue({ data: [] }),
  createFaq: vi.fn(),
  updateFaq: vi.fn(),
  deleteFaq: vi.fn(),
  reorderFaqs: vi.fn(),
}))

import FaqListView from '@/views/board/FaqListView.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      faq: {
        title: 'FAQ 관리',
        add: 'FAQ 추가',
        empty: '등록된 FAQ가 없습니다',
        field: { category: '카테고리', status: '상태' },
        status: { PUBLISHED: '공개', HIDDEN: '숨김' },
      },
      common: { search: '검색' },
    },
  },
})

function mountView() {
  return mount(FaqListView, {
    global: { plugins: [i18n, createTestingPinia({ createSpy: vi.fn })] },
  })
}

describe('FaqListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('FAQ 관리 제목을 렌더링한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('FAQ 관리')
  })

  it('검색 버튼을 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    const btns = wrapper.findAll('button').map((b) => b.text())
    expect(btns.some((t) => t.includes('검색'))).toBe(true)
  })

  it('상태 셀렉트 옵션 텍스트를 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    // status select placeholder
    expect(wrapper.text()).toContain('상태')
  })
})
