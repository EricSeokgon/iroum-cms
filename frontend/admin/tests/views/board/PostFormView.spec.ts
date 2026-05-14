/**
 * PostFormView 단위 테스트 — SPEC-CMS-003 REQ-BOARD-004
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import PostFormView from '../../../src/views/board/PostFormView.vue'

vi.mock('../../../src/api/board', () => ({
  boardApi: {
    getPost: vi.fn(),
    createPost: vi.fn(),
    updatePost: vi.fn(),
    uploadAttachment: vi.fn(),
  },
}))

import { boardApi } from '../../../src/api/board'

const mockCreatePost = vi.mocked(boardApi.createPost)

function buildWrapper(mode: 'create' | 'edit' = 'create') {
  const pinia = createPinia()
  setActivePinia(pinia)

  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    messages: {
      ko: {
        board: {
          posts: {
            write: '글쓰기',
            edit: '수정',
            uploadFile: '파일 첨부',
            uploadTip: '최대 10MB',
            field: {
              title: '제목', titlePlaceholder: '제목 입력', content: '본문', contentPlaceholder: '내용 입력',
              isNotice: '공지', isNoticeLabel: '상단 공지', categoryCode: '카테고리', categoryPlaceholder: '선택', attachments: '첨부파일',
            },
            error: { titleRequired: '제목 필수', contentRequired: '본문 필수', titleLength: '500자 이하', loadFailed: '불러오기 실패', saveFailed: '저장 실패', uploadFailed: '업로드 실패', fileLimit: '5개 이하' },
            success: { created: '등록됨', updated: '수정됨', uploaded: '업로드됨' },
          },
        },
        common: { save: '저장', cancel: '취소' },
      },
    },
  })

  const router = createRouter({ history: createWebHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] })

  const props = mode === 'create' ? { bbsId: '1' } : { id: '1' }
  return mount(PostFormView, {
    props,
    global: {
      plugins: [pinia, i18n, router, ElementPlus],
    },
  })
}

describe('PostFormView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCreatePost.mockResolvedValue({ data: { id: 10, bbsId: 1, title: 'T', authorUsername: 'a', viewCount: 0, likeCount: 0, status: 'PUBLISHED', isNotice: false, contentHtml: '', attachments: [], createdAt: '', updatedAt: '' } } as never)
  })

  it('생성 모드에서 제목 입력 필드가 렌더링된다', async () => {
    const wrapper = buildWrapper('create')
    await flushPromises()
    expect(wrapper.find('#post-title').exists()).toBe(true)
  })

  it('생성 모드에서 저장 버튼이 존재한다', () => {
    const wrapper = buildWrapper('create')
    expect(wrapper.html()).toContain('저장')
  })
})
