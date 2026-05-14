// SPEC-CMS-PUBLIC-001 T-003 — menuStore 테스트
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const fetchMock = vi.fn()

vi.mock('@/api/menuApi', () => ({
  menuApi: {
    getPublicMenus: (...args: unknown[]) => fetchMock(...args),
  },
}))

const sampleTree = [
  { id: 1, code: 'NOTICE', name: '공지', path: '/notices', parentId: null, depth: 1, sortOrder: 1 },
  { id: 2, code: 'POLICY', name: '정책', path: '/policies', parentId: null, depth: 1, sortOrder: 2 },
]

describe('menuStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchMock.mockReset()
    fetchMock.mockResolvedValue(sampleTree)
  })

  it('초기 상태는 빈 메뉴 트리 + isLoaded=false', async () => {
    const { useMenuStore } = await import('@/stores/menuStore')
    const store = useMenuStore()
    expect(store.menus).toEqual([])
    expect(store.isLoaded).toBe(false)
  })

  it('fetchMenus 호출 시 menuApi로부터 트리를 받아 저장한다', async () => {
    const { useMenuStore } = await import('@/stores/menuStore')
    const store = useMenuStore()
    await store.fetchMenus()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(store.menus).toEqual(sampleTree)
    expect(store.isLoaded).toBe(true)
  })

  it('60초 캐시 — isLoaded=true이면 재호출하지 않는다', async () => {
    const { useMenuStore } = await import('@/stores/menuStore')
    const store = useMenuStore()
    await store.fetchMenus()
    await store.fetchMenus() // 두 번째 호출 — 캐시
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('reload는 강제 재호출한다', async () => {
    const { useMenuStore } = await import('@/stores/menuStore')
    const store = useMenuStore()
    await store.fetchMenus()
    await store.reload()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
