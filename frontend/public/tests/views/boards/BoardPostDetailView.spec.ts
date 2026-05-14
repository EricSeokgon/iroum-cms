// SPEC-CMS-PUBLIC-001 T-006 — BoardPostDetailView 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { PostDetail } from '@iroum/shared/types/api'

const postMock = vi.fn()
vi.mock('@/api/boardApi', () => ({
  boardApi: {
    master: vi.fn(),
    posts: vi.fn(),
    post: (...args: unknown[]) => postMock(...args),
  },
}))

const clientPostMock = vi.fn()
vi.mock('@/api/client', () => ({
  apiClient: { post: (...args: unknown[]) => clientPostMock(...args) },
  ACCESS_TOKEN_KEY: 'public.accessToken',
  REFRESH_TOKEN_KEY: 'public.refreshToken',
}))

function makeDetail(): PostDetail {
  return {
    id: 7,
    bbsId: 1,
    title: '게시글 제목',
    authorUsername: 'user',
    viewCount: 5,
    likeCount: 0,
    status: 'PUBLISHED',
    isNotice: false,
    publishedAt: '2026-04-15T09:00:00Z',
    createdAt: '2026-04-15T09:00:00Z',
    updatedAt: '2026-04-15T09:00:00Z',
    contentHtml: '<p>본문 내용</p>',
    attachments: [],
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
      { path: '/boards/:code', name: 'board-post-list', component: { template: '<div />' } },
      {
        path: '/boards/:code/posts/:id',
        name: 'board-post-detail',
        component: () => import('@/views/boards/BoardPostDetailView.vue'),
      },
    ],
  })
  router.push('/boards/COMMUNITY/posts/7')
  await router.isReady()
  const BoardPostDetailView = (await import('@/views/boards/BoardPostDetailView.vue')).default
  const wrapper = mount(BoardPostDetailView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('BoardPostDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    postMock.mockReset()
    clientPostMock.mockReset()
    localStorage.clear()
  })

  it('mount 시 boardApi.post(id) 호출', async () => {
    postMock.mockResolvedValue(makeDetail())
    await mountView()
    expect(postMock).toHaveBeenCalledWith(7)
  })

  it('본문이 NoticeContent 로 sanitize 되어 렌더', async () => {
    const detail = makeDetail()
    detail.contentHtml = '<p>안전</p><script>alert(1)</script>'
    postMock.mockResolvedValue(detail)
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="notice-content"]').exists()).toBe(true)
    expect(wrapper.html()).toContain('안전')
    expect(wrapper.html()).not.toContain('<script')
  })

  it('첨부가 비어있으면 첨부 섹션 미렌더링', async () => {
    postMock.mockResolvedValue(makeDetail())
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="attachment-section"]').exists()).toBe(false)
  })
})
