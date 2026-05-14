// SPEC-CMS-PUBLIC-001 T-006 — NoticeListView 검증 (B-01, B-02, B-03)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { PostSummary } from '@iroum/shared/types/api'

const listMock = vi.fn()
vi.mock('@/api/noticeApi', () => ({
  noticeApi: {
    list: (...args: unknown[]) => listMock(...args),
    detail: vi.fn(),
  },
}))

function makePost(id: number, isNotice = false): PostSummary {
  return {
    id,
    bbsId: 1,
    title: `공지 ${id}`,
    authorUsername: 'admin',
    viewCount: 10,
    likeCount: 0,
    status: 'PUBLISHED',
    isNotice,
    publishedAt: '2026-04-15T09:00:00Z',
    createdAt: '2026-04-15T09:00:00Z',
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
      {
        path: '/notices',
        name: 'notice-list',
        component: () => import('@/views/notices/NoticeListView.vue'),
      },
      { path: '/notices/:id', name: 'notice-detail', component: { template: '<div />' } },
    ],
  })
  router.push('/notices')
  await router.isReady()
  const NoticeListView = (await import('@/views/notices/NoticeListView.vue')).default
  const wrapper = mount(NoticeListView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('NoticeListView — B-01 페이징', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    localStorage.clear()
  })

  it('마운트 시 noticeApi.list({page:0, size:20}) 호출', async () => {
    listMock.mockResolvedValue({
      content: Array.from({ length: 20 }, (_, i) => makePost(i + 1)),
      page: 0,
      size: 20,
      totalElements: 40,
      totalPages: 2,
    })
    await mountView()
    expect(listMock).toHaveBeenCalledTimes(1)
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }))
  })

  it('20개 카드와 PaginationBar(nav aria-label="페이지네이션") 렌더링', async () => {
    listMock.mockResolvedValue({
      content: Array.from({ length: 20 }, (_, i) => makePost(i + 1)),
      page: 0,
      size: 20,
      totalElements: 40,
      totalPages: 2,
    })
    const { wrapper } = await mountView()
    const cards = wrapper.findAll('[data-testid="notice-card"]')
    expect(cards.length).toBe(20)
    expect(wrapper.find('nav[aria-label="페이지네이션"]').exists()).toBe(true)
  })
})

describe('NoticeListView — B-02 카테고리·키워드 검색', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    localStorage.clear()
  })

  it('카테고리 + 키워드 입력 후 검색 → noticeApi.list 가 categoryCode + keyword 와 함께 호출', async () => {
    listMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const { wrapper } = await mountView()

    await wrapper.find('[data-testid="notice-category-select"]').setValue('EVENT')
    await wrapper.find('[data-testid="notice-keyword-input"]').setValue('세미나')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const lastCall = listMock.mock.calls[listMock.mock.calls.length - 1][0]
    expect(lastCall).toMatchObject({
      page: 0,
      size: 20,
      categoryCode: 'EVENT',
      keyword: '세미나',
    })
  })

  it('0건 결과 → EmptyState + 초기화 버튼 렌더링', async () => {
    listMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="empty-reset"]').exists()).toBe(true)
  })
})

describe('NoticeListView — F-05 네트워크 실패 재시도', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    localStorage.clear()
  })

  it('첫 실패 시 ErrorState + 재시도 버튼이 표시된다', async () => {
    listMock.mockRejectedValue(new Error('network'))
    const { wrapper } = await mountView()
    const err = wrapper.find('[data-testid="notice-error-state"]')
    expect(err.exists()).toBe(true)
    // 재시도 버튼 노출 (기본 메시지)
    expect(err.text()).toContain('다시 시도')
  })

  it('재시도 버튼 클릭 시 loadNotices 가 재호출된다', async () => {
    listMock.mockRejectedValue(new Error('network'))
    const { wrapper } = await mountView()
    expect(listMock).toHaveBeenCalledTimes(1)
    // ErrorState root 의 data-testid 는 부모(NoticeListView)가 전달한 'notice-error-state' 로 fallthrough
    await wrapper.find('[data-testid="notice-error-state"] button').trigger('click')
    await flushPromises()
    expect(listMock).toHaveBeenCalledTimes(2)
  })

  it('3회 연속 실패 후 persistent error 메시지가 표시되고 재시도 버튼이 사라진다', async () => {
    listMock.mockRejectedValue(new Error('network'))
    const { wrapper } = await mountView()
    // 1회차는 mount 에서 이미 실패 → 2회차 + 3회차 재시도 트리거
    await wrapper.find('[data-testid="notice-error-state"] button').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="notice-error-state"] button').trigger('click')
    await flushPromises()
    // 이제 3회 실패 → persistent
    expect(listMock).toHaveBeenCalledTimes(3)
    expect(wrapper.text()).toContain('지속적으로 실패합니다')
    // retry 버튼 미노출 (showRetry=false)
    expect(wrapper.find('[data-testid="notice-error-state"] button').exists()).toBe(false)
  })
})

describe('NoticeListView — B-03 상단 고정', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    localStorage.clear()
  })

  it('page=0 에서 isNotice=true 항목은 pinned 섹션에 분리', async () => {
    listMock.mockResolvedValue({
      content: [
        makePost(1, true),
        makePost(2, true),
        makePost(3),
        makePost(4),
      ],
      page: 0,
      size: 20,
      totalElements: 4,
      totalPages: 1,
    })
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="pinned-section"]').exists()).toBe(true)
    const pinnedSection = wrapper.find('[data-testid="pinned-section"]')
    // pinned 섹션에 2개 항목
    expect(pinnedSection.findAll('[data-testid="notice-card"]').length).toBe(2)
    // 일반 목록은 2개 (isNotice=false)
    const normalList = wrapper.find('[data-testid="notice-list"]')
    expect(normalList.findAll('[data-testid="notice-card"]').length).toBe(2)
  })
})
