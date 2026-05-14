// SPEC-CMS-PUBLIC-001 T-003 — searchStore 테스트
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const searchMock = vi.fn()

vi.mock('@/api/searchApi', () => ({
  searchApi: {
    search: (...args: unknown[]) => searchMock(...args),
  },
}))

describe('searchStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    searchMock.mockReset()
  })

  it('초기 상태는 빈 결과 + isLoading=false', async () => {
    const { useSearchStore } = await import('@/stores/searchStore')
    const store = useSearchStore()
    expect(store.results).toEqual([])
    expect(store.isLoading).toBe(false)
    expect(store.totalCount).toBe(0)
    expect(store.query).toBe('')
  })

  it('search 호출 시 결과를 저장한다', async () => {
    searchMock.mockResolvedValue({
      totalElements: 2,
      page: 0,
      size: 20,
      results: [
        { id: 1, type: 'POST', title: '공지A', snippet: '...', url: '/notices/1', score: 0.9 },
        { id: 2, type: 'FAQ', title: 'FAQ B', snippet: '...', url: '/faqs#2', score: 0.8 },
      ],
    })
    const { useSearchStore } = await import('@/stores/searchStore')
    const store = useSearchStore()
    await store.search('test', 'ALL')
    expect(store.query).toBe('test')
    expect(store.currentType).toBe('ALL')
    expect(store.totalCount).toBe(2)
    expect(store.results).toHaveLength(2)
  })

  it('clearResults는 결과를 비운다', async () => {
    searchMock.mockResolvedValue({ totalElements: 1, page: 0, size: 20, results: [{ id: 1, type: 'POST', title: 'x', snippet: '', url: '/', score: 0 }] })
    const { useSearchStore } = await import('@/stores/searchStore')
    const store = useSearchStore()
    await store.search('test')
    store.clearResults()
    expect(store.results).toEqual([])
    expect(store.totalCount).toBe(0)
    expect(store.query).toBe('')
  })
})
