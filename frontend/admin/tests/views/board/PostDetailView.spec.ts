/**
 * PostDetailView 단위 테스트 — SPEC-CMS-003 REQ-BOARD-003
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import PostDetailView from '../../../src/views/board/PostDetailView.vue'
import type { PostDetail } from '@iroum/shared/types/api'

vi.mock('../../../src/api/board', () => ({
  boardApi: {
    getPost: vi.fn(),
    deletePost: vi.fn(),
    getAttachmentUrl: vi.fn(),
  },
}))

vi.mock('../../../src/components/PostCommentSection.vue', () => ({
  default: { template: '<div />' },
}))

import { boardApi } from '../../../src/api/board'

const mockGetPost = vi.mocked(boardApi.getPost)

const MOCK_POST: PostDetail = {
  id: 1,
  bbsId: 1,
  title: '테스트 게시글',
  contentHtml: '<p>본문 내용</p>',
  authorUsername: 'admin',
  viewCount: 10,
  likeCount: 0,
  status: 'PUBLISHED',
  isNotice: false,
  attachments: [],
  createdAt: '2026-04-01T12:00:00Z',
  updatedAt: '2026-04-01T12:00:00Z',
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
          posts: {
            title: '게시글',
            edit: '수정',
            delete: '삭제',
            download: '다운로드',
            notice: '공지',
            content: '본문',
            field: { author: '작성자', viewCount: '조회수', createdAt: '작성일', attachments: '첨부파일' },
            confirm: { delete: '삭제 확인' },
            error: { loadFailed: '불러오기 실패', deleteFailed: '삭제 실패', downloadFailed: '다운로드 실패', notFound: '없음' },
            success: { deleted: '삭제됨' },
          },
        },
        common: { back: '뒤로', cancel: '취소' },
      },
    },
  })

  const router = createRouter({ history: createWebHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] })

  return mount(PostDetailView, {
    props: { id: '1' },
    global: {
      plugins: [pinia, i18n, router, ElementPlus],
      stubs: { 'i-ep-paperclip': true, PostCommentSection: true },
    },
  })
}

describe('PostDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetPost.mockResolvedValue({ data: MOCK_POST } as never)
  })

  it('마운트 시 게시글 상세 API를 호출한다', async () => {
    buildWrapper()
    await new Promise((r) => setTimeout(r, 50))
    expect(mockGetPost).toHaveBeenCalledWith(1)
  })

  it('게시글 제목과 본문이 렌더링된다', async () => {
    const wrapper = buildWrapper()
    await new Promise((r) => setTimeout(r, 50))
    expect(wrapper.html()).toContain('테스트 게시글')
    expect(wrapper.html()).toContain('본문 내용')
  })
})
