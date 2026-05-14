// SPEC-CMS-PUBLIC-001 T-006 — QnaDetailView 검증 (B-08 비공개 게시글 접근)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { QnaDetail } from '@/api/qnaApi'

const detailMock = vi.fn()
vi.mock('@/api/qnaApi', () => ({
  qnaApi: {
    list: vi.fn(),
    detail: (...args: unknown[]) => detailMock(...args),
    create: vi.fn(),
  },
}))

function makeDetail(): QnaDetail {
  return {
    id: 1,
    title: '문의',
    authorUsername: 'user1',
    status: 'ANSWERED',
    isPrivate: false,
    createdAt: '2026-04-15T09:00:00Z',
    questionHtml: '<p>질문 본문</p>',
    answerHtml: '<p>답변 본문</p>',
    answeredAt: '2026-04-15T10:00:00Z',
  }
}

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/qnas', name: 'qna-list', component: { template: '<div />' } },
      {
        path: '/qnas/:id',
        name: 'qna-detail',
        component: () => import('@/views/qnas/QnaDetailView.vue'),
      },
      { path: '/:pathMatch(.*)*', name: 'not-found', component: { template: '<div>404</div>' } },
    ],
  })
  router.push('/qnas/1')
  await router.isReady()
  const QnaDetailView = (await import('@/views/qnas/QnaDetailView.vue')).default
  const wrapper = mount(QnaDetailView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('QnaDetailView — 정상 로드', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detailMock.mockReset()
    localStorage.clear()
  })

  it('질문 + 답변 본문이 sanitize 되어 렌더링된다', async () => {
    detailMock.mockResolvedValue(makeDetail())
    const { wrapper } = await mountView()
    expect(wrapper.text()).toContain('문의')
    expect(wrapper.html()).toContain('질문 본문')
    expect(wrapper.html()).toContain('답변 본문')
  })

  it('답변이 없으면 "아직 답변이 등록되지 않았습니다" 표시', async () => {
    detailMock.mockResolvedValue({ ...makeDetail(), answerHtml: undefined, status: 'PENDING' })
    const { wrapper } = await mountView()
    expect(wrapper.text()).toContain('아직 답변이 등록되지 않았습니다')
  })
})

describe('QnaDetailView — B-08 비공개 게시글 접근 (404 → not-found)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detailMock.mockReset()
    localStorage.clear()
  })

  it('상세 호출이 404 응답 → router 가 not-found 라우트로 replace', async () => {
    const axiosError = {
      isAxiosError: true,
      response: { status: 404, data: { code: 'NOT_FOUND' } },
    }
    detailMock.mockRejectedValue(axiosError)
    const { router } = await mountView()
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('not-found')
  })
})
