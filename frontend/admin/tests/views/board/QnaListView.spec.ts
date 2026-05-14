// QnaListView 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/qna', () => ({
  listQnas: vi.fn().mockResolvedValue({
    data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
  }),
  getQna: vi.fn(),
  createQna: vi.fn(),
  updateQna: vi.fn(),
  answerQna: vi.fn(),
  deleteQna: vi.fn(),
}))

import QnaListView from '@/views/board/QnaListView.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      qna: {
        title: 'Q&A 관리',
        empty: '등록된 Q&A가 없습니다',
        field: { status: '상태', isPrivate: '공개여부', title: '제목' },
        status: { PENDING: '미답변', ANSWERED: '답변완료', CLOSED: '종료', HIDDEN: '숨김' },
        privacy: { all: '전체', public: '공개', private: '비공개' },
      },
      common: { search: '검색' },
    },
  },
})

function mountView() {
  return mount(QnaListView, {
    global: { plugins: [i18n, createTestingPinia({ createSpy: vi.fn })] },
  })
}

describe('QnaListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('Q&A 관리 제목을 렌더링한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Q&A 관리')
  })

  it('검색 버튼을 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    const btns = wrapper.findAll('button').map((b) => b.text())
    expect(btns.some((t) => t.includes('검색'))).toBe(true)
  })

  it('상태 셀렉트와 공개여부 필터를 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('상태')
    expect(wrapper.text()).toContain('공개여부')
  })
})
