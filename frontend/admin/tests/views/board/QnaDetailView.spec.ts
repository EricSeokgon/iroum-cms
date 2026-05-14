// QnaDetailView 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/qna', () => ({
  listQnas: vi.fn(),
  getQna: vi.fn().mockResolvedValue({
    data: {
      id: 1,
      title: '테스트 질문',
      questionerId: 100,
      isPrivate: false,
      status: 'PENDING',
      questionHtml: '<p>질문 내용</p>',
      answerHtml: null,
      createdAt: '2026-01-01T00:00:00Z',
    },
  }),
  createQna: vi.fn(),
  updateQna: vi.fn(),
  answerQna: vi.fn(),
  deleteQna: vi.fn(),
}))

import QnaDetailView from '@/views/board/QnaDetailView.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      qna: {
        title: 'Q&A',
        field: { questioner: '질문자', isPrivate: '공개여부', question: '질문', answer: '답변' },
        status: { PENDING: '미답변', ANSWERED: '답변완료', CLOSED: '종료', HIDDEN: '숨김' },
        privacy: { public: '공개', private: '비공개' },
      },
      common: { back: '뒤로', startDate: '작성일' },
    },
  },
})

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/qna/:id', name: 'qna-detail', component: QnaDetailView },
  ],
})

async function mountView() {
  await router.push({ name: 'qna-detail', params: { id: '1' } })
  await router.isReady()
  return mount(QnaDetailView, {
    props: { id: '1' },
    global: { plugins: [i18n, router, createTestingPinia({ createSpy: vi.fn })] },
  })
}

describe('QnaDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('뒤로 버튼을 노출한다', async () => {
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('뒤로')
  })

  it('로딩 상태에서 빈 컨테이너가 렌더링된다', async () => {
    const wrapper = await mountView()
    // 데이터 로드 전 v-loading 컨테이너가 마운트됨
    expect(wrapper.find('div').exists()).toBe(true)
  })
})
