// SPEC-CMS-PUBLIC-001 T-006 — BoardPostListView 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { PostSummary, BbsMasterDetail } from '@iroum/shared/types/api'

const masterMock = vi.fn()
const postsMock = vi.fn()
vi.mock('@/api/boardApi', () => ({
  boardApi: {
    master: (...args: unknown[]) => masterMock(...args),
    posts: (...args: unknown[]) => postsMock(...args),
    post: vi.fn(),
  },
}))

const sampleMaster: BbsMasterDetail = {
  id: 7,
  code: 'COMMUNITY',
  name: '커뮤니티',
  type: 'NORMAL',
  useComment: true,
  useAttachment: true,
  status: 'ACTIVE',
  createdAt: '2026-04-01T00:00:00Z',
  description: '커뮤니티 게시판입니다',
  maxAttachmentCount: 3,
  maxAttachmentSizeKb: 1024,
  allowAnonymous: false,
}

function makePost(id: number): PostSummary {
  return {
    id,
    bbsId: 7,
    title: `게시글 ${id}`,
    authorUsername: 'user',
    viewCount: id,
    likeCount: 0,
    status: 'PUBLISHED',
    isNotice: false,
    publishedAt: '2026-04-15T09:00:00Z',
    createdAt: '2026-04-15T09:00:00Z',
  }
}

async function mountView(code = 'COMMUNITY') {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/boards/:code',
        name: 'board-post-list',
        component: () => import('@/views/boards/BoardPostListView.vue'),
      },
      {
        path: '/boards/:code/posts/:id',
        name: 'board-post-detail',
        component: { template: '<div />' },
      },
    ],
  })
  router.push(`/boards/${code}`)
  await router.isReady()
  const BoardPostListView = (await import('@/views/boards/BoardPostListView.vue')).default
  const wrapper = mount(BoardPostListView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('BoardPostListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    masterMock.mockReset()
    postsMock.mockReset()
    localStorage.clear()
  })

  it('마운트 시 master(code) 호출 후 posts(bbsId) 를 페이지 0으로 호출', async () => {
    masterMock.mockResolvedValue(sampleMaster)
    postsMock.mockResolvedValue({
      content: [makePost(1), makePost(2)],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })
    await mountView('COMMUNITY')
    expect(masterMock).toHaveBeenCalledWith('COMMUNITY')
    expect(postsMock).toHaveBeenCalledWith(7, expect.objectContaining({ page: 0, size: 20 }))
  })

  it('게시판 이름 + 게시글 목록 렌더링', async () => {
    masterMock.mockResolvedValue(sampleMaster)
    postsMock.mockResolvedValue({
      content: [makePost(1), makePost(2)],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('커뮤니티')
    expect(wrapper.text()).toContain('게시글 1')
    expect(wrapper.text()).toContain('게시글 2')
  })

  it('검색 키워드 입력 후 제출 → posts 가 keyword 와 함께 재호출', async () => {
    masterMock.mockResolvedValue(sampleMaster)
    postsMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const wrapper = await mountView()
    postsMock.mockClear()
    await wrapper.find('[data-testid="board-keyword-input"]').setValue('공지')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const lastCall = postsMock.mock.calls[postsMock.mock.calls.length - 1]
    expect(lastCall[1]).toMatchObject({ keyword: '공지' })
  })

  it('게시글이 없을 때 EmptyState 표시', async () => {
    masterMock.mockResolvedValue(sampleMaster)
    postsMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
  })
})
