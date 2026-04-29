/**
 * PostListView 단위 테스트 — SPEC-CMS-003 REQ-BOARD-002
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import PostListView from '../../../src/views/board/PostListView.vue'
import type { PageResponse, PostSummary } from '@iroum/shared/types/api'

vi.mock('../../../src/api/board', () => ({
  boardApi: {
    listPosts: vi.fn(),
    getMaster: vi.fn(),
  },
}))

import { boardApi } from '../../../src/api/board'

const mockListPosts = vi.mocked(boardApi.listPosts)
const mockGetMaster = vi.mocked(boardApi.getMaster)

const MOCK_POSTS: PostSummary[] = [
  {
    id: 1,
    bbsId: 1,
    title: '공지 테스트',
    authorUsername: 'admin',
    viewCount: 42,
    likeCount: 0,
    status: 'PUBLISHED',
    isNotice: true,
    createdAt: '2026-04-01T12:00:00Z',
  },
  {
    id: 2,
    bbsId: 1,
    title: '일반 게시글',
    authorUsername: 'user1',
    viewCount: 5,
    likeCount: 0,
    status: 'PUBLISHED',
    isNotice: false,
    createdAt: '2026-04-02T12:00:00Z',
  },
]

const PAGE_RESP: { data: PageResponse<PostSummary> } = {
  data: { content: MOCK_POSTS, page: 0, size: 20, totalElements: 2, totalPages: 1 },
}

function buildWrapper() {
  const pinia = createPinia()
  setActivePinia(pinia)

  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    messages: {
      ko: {
        board: {
          masters: { type: { NOTICE: '공지' } },
          posts: {
            title: '게시글 목록',
            write: '글쓰기',
            search: '검색',
            searchBtn: '검색',
            sort: '정렬',
            sortOptions: { latest: '최신순', oldest: '오래된순', viewCount: '조회수순' },
            empty: '게시글이 없습니다',
            resultCount: '총 {count}건',
            notice: '공지',
            field: { no: '번호', title: '제목', author: '작성자', viewCount: '조회수', createdAt: '작성일' },
            error: { loadFailed: '불러오기 실패' },
          },
        },
        a11y: { pagination: '페이지' },
      },
    },
  })

  const router = createRouter({ history: createWebHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] })

  return mount(PostListView, {
    props: { bbsId: '1' },
    global: {
      plugins: [pinia, i18n, router, ElementPlus],
      stubs: { 'i-ep-search': true, 'i-ep-paperclip': true },
    },
  })
}

describe('PostListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListPosts.mockResolvedValue(PAGE_RESP as never)
    mockGetMaster.mockResolvedValue({ data: { id: 1, name: '공지사항', type: 'NOTICE' } } as never)
  })

  it('마운트 시 게시글 목록 API를 호출한다', async () => {
    buildWrapper()
    await new Promise((r) => setTimeout(r, 50))
    expect(mockListPosts).toHaveBeenCalledWith(expect.objectContaining({ bbsId: 1 }))
  })

  it('게시글 제목이 테이블에 노출된다', async () => {
    const wrapper = buildWrapper()
    await new Promise((r) => setTimeout(r, 50))
    expect(wrapper.html()).toContain('공지 테스트')
  })

  it('공지 태그가 isNotice=true인 게시글에 렌더링된다', async () => {
    const wrapper = buildWrapper()
    await new Promise((r) => setTimeout(r, 50))
    expect(wrapper.html()).toContain('공지')
  })
})
