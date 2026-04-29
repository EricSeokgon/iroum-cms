/**
 * BoardListView 단위 테스트 — SPEC-CMS-003 REQ-BOARD-001
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import BoardListView from '../../../src/views/board/BoardListView.vue'
import type { BbsMasterSummary } from '@iroum/shared/types/api'

vi.mock('../../../src/api/board', () => ({
  boardApi: {
    listMasters: vi.fn(),
    deleteMaster: vi.fn(),
  },
}))

vi.mock('../../../src/views/board/BoardFormView.vue', () => ({
  default: { template: '<div />' },
}))

import { boardApi } from '../../../src/api/board'

const mockListMasters = vi.mocked(boardApi.listMasters)

const MOCK_MASTERS: BbsMasterSummary[] = [
  {
    id: 1,
    code: 'NOTICE',
    name: '공지사항',
    type: 'NOTICE',
    useComment: false,
    useAttachment: true,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    code: 'FAQ',
    name: 'FAQ',
    type: 'FAQ',
    useComment: false,
    useAttachment: false,
    status: 'ACTIVE',
    createdAt: '2026-01-02T00:00:00Z',
  },
]

function buildWrapper() {
  const pinia = createPinia()
  setActivePinia(pinia)

  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    messages: {
      ko: {
        board: {
          masters: {
            title: '게시판 관리',
            add: '게시판 추가',
            edit: '수정',
            delete: '삭제',
            empty: '등록된 게시판이 없습니다',
            loaded: '{count}개 게시판 로드됨',
            field: { code: '코드', name: '이름', type: '유형', useComment: '댓글 허용', useAttachment: '첨부 허용', status: '상태', createdAt: '생성일' },
            type: { NORMAL: '일반', NOTICE: '공지', QNA: 'Q&A', FAQ: 'FAQ', GALLERY: '갤러리', PUBLICATION: '발간자료', SURVEY: '설문' },
            confirm: { delete: '삭제 확인' },
            error: { loadFailed: '불러오기 실패', deleteFailed: '삭제 실패' },
            success: { deleted: '삭제됨' },
          },
        },
        common: { cancel: '취소', actions: '액션', yes: '예', no: '아니오' },
      },
    },
  })

  const router = createRouter({ history: createWebHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] })

  return mount(BoardListView, {
    global: {
      plugins: [pinia, i18n, router, ElementPlus],
      stubs: { 'i-ep-search': true },
    },
  })
}

describe('BoardListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListMasters.mockResolvedValue({ data: MOCK_MASTERS } as never)
  })

  it('마운트 시 게시판 목록을 로드한다', async () => {
    const wrapper = buildWrapper()
    await new Promise((r) => setTimeout(r, 50))
    expect(mockListMasters).toHaveBeenCalledTimes(1)
  })

  it('게시판 이름이 테이블에 렌더링된다', async () => {
    const wrapper = buildWrapper()
    await new Promise((r) => setTimeout(r, 50))
    const html = wrapper.html()
    expect(html).toContain('공지사항')
    expect(html).toContain('FAQ')
  })

  it('빈 목록일 때 empty 메시지를 표시한다', async () => {
    mockListMasters.mockResolvedValue({ data: [] } as never)
    const wrapper = buildWrapper()
    await new Promise((r) => setTimeout(r, 50))
    expect(wrapper.html()).toContain('등록된 게시판이 없습니다')
  })
})
