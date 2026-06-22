// ReviewManagementView 단위 테스트 — SPEC-CMS-REVIEW-001 C2
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// 공통 클라이언트 모킹 (api/reviews 가 의존)
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))

vi.mock('@/api/reviews', () => ({
  listAdminReviews: vi.fn(),
  hideAdminReview: vi.fn(),
  deleteAdminReview: vi.fn(),
}))

// ElMessageBox.confirm 을 즉시 resolve 하도록 스텁
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
  }
})

import ReviewManagementView from '@/views/board/ReviewManagementView.vue'
import { listAdminReviews, hideAdminReview, deleteAdminReview } from '@/api/reviews'

const mockReviews = [
  {
    id: 1,
    postId: 10,
    authorId: 100,
    authorName: '홍길동',
    rating: 5,
    content: '아주 유용한 게시물입니다',
    status: 'VISIBLE' as const,
    ipAddress: '127.0.0.1',
    createdAt: '2026-06-22T00:00:00Z',
  },
  {
    id: 2,
    postId: 10,
    authorId: null,
    authorName: null,
    rating: 2,
    content: null,
    status: 'HIDDEN' as const,
    ipAddress: null,
    createdAt: '2026-06-21T00:00:00Z',
  },
]

function mockPage(content: typeof mockReviews) {
  return {
    data: {
      content,
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: 1,
    },
  }
}

function mountView() {
  return mount(ReviewManagementView, {
    global: { plugins: [ElementPlus] },
  })
}

describe('ReviewManagementView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listAdminReviews).mockResolvedValue(mockPage(mockReviews) as never)
    vi.mocked(hideAdminReview).mockResolvedValue(undefined as never)
    vi.mocked(deleteAdminReview).mockResolvedValue(undefined as never)
  })

  it('마운트되며 제목과 목록을 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('리뷰 관리')
    expect(listAdminReviews).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('홍길동')
    expect(wrapper.text()).toContain('아주 유용한 게시물입니다')
  })

  it('마운트 시 페이지 0, size 20, status ALL 로 조회한다', async () => {
    mountView()
    await flushPromises()

    expect(listAdminReviews).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20, status: 'ALL' }),
    )
  })

  it('게시물 ID 필터를 숫자로 변환해 전달한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    vi.mocked(listAdminReviews).mockClear()

    const input = wrapper.find('input[aria-label="게시물 ID 필터"]')
    await input.setValue('10')
    await input.trigger('keyup.enter')
    await flushPromises()

    expect(listAdminReviews).toHaveBeenCalledWith(
      expect.objectContaining({ postId: 10, page: 0 }),
    )
  })

  it('숨김 액션은 hideAdminReview 를 호출하고 목록을 갱신한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    vi.mocked(listAdminReviews).mockClear()

    // 첫 행(VISIBLE)의 숨김 버튼
    const hideBtn = wrapper
      .findAll('button')
      .find((b) => b.text() === '숨김' && !b.attributes('disabled'))
    expect(hideBtn).toBeTruthy()
    await hideBtn!.trigger('click')
    await flushPromises()

    expect(hideAdminReview).toHaveBeenCalledWith(1)
    expect(listAdminReviews).toHaveBeenCalledTimes(1)
  })

  it('삭제 액션은 deleteAdminReview 를 호출한다', async () => {
    const wrapper = mountView()
    await flushPromises()

    const deleteBtn = wrapper
      .findAll('button')
      .find((b) => b.text() === '삭제' && !b.attributes('disabled'))
    expect(deleteBtn).toBeTruthy()
    await deleteBtn!.trigger('click')
    await flushPromises()

    expect(deleteAdminReview).toHaveBeenCalledWith(1)
  })

  it('상태 라벨/태그를 한국어로 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('노출')
    expect(wrapper.text()).toContain('숨김')
  })
})
