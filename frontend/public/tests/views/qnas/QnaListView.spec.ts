// SPEC-CMS-PUBLIC-001 T-006 — QnaListView 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const listMock = vi.fn()
vi.mock('@/api/qnaApi', () => ({
  qnaApi: {
    list: (...args: unknown[]) => listMock(...args),
    detail: vi.fn(),
    create: vi.fn(),
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
    routes: [
      { path: '/qnas', name: 'qna-list', component: () => import('@/views/qnas/QnaListView.vue') },
      { path: '/qnas/new', name: 'qna-create', component: { template: '<div />' } },
      { path: '/qnas/:id', name: 'qna-detail', component: { template: '<div />' } },
    ],
  })
  router.push('/qnas')
  await router.isReady()
  const QnaListView = (await import('@/views/qnas/QnaListView.vue')).default
  const wrapper = mount(QnaListView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('QnaListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    localStorage.clear()
  })

  it('마운트 시 qnaApi.list({page:0, size:20}) 호출', async () => {
    listMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    await mountView()
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }))
  })

  it('Q&A 작성 링크 → /qnas/new 라우트', async () => {
    listMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const wrapper = await mountView()
    const link = wrapper.find('[data-testid="qna-create-link"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe('/qnas/new')
  })

  it('답변 완료 항목은 "답변 완료" 상태 뱃지를 가진다', async () => {
    listMock.mockResolvedValue({
      content: [
        {
          id: 1,
          title: '문의 1',
          authorUsername: 'user1',
          status: 'ANSWERED',
          isPrivate: false,
          createdAt: '2026-04-15T09:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('답변 완료')
    expect(wrapper.text()).toContain('문의 1')
  })

  it('비공개 항목은 비공개 뱃지를 표시한다', async () => {
    listMock.mockResolvedValue({
      content: [
        {
          id: 2,
          title: '비공개 문의',
          authorUsername: 'user1',
          status: 'ANSWERED',
          isPrivate: true,
          createdAt: '2026-04-15T09:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('비공개')
  })
})
