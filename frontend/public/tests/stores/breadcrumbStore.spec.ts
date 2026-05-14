// SPEC-CMS-PUBLIC-001 T-003 — breadcrumbStore 테스트
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

describe('breadcrumbStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('초기 상태는 빈 항목 배열', async () => {
    const { useBreadcrumbStore } = await import('@/stores/breadcrumbStore')
    const store = useBreadcrumbStore()
    expect(store.items).toEqual([])
  })

  it('set은 브레드크럼 항목을 교체한다', async () => {
    const { useBreadcrumbStore } = await import('@/stores/breadcrumbStore')
    const store = useBreadcrumbStore()
    store.set([
      { label: '홈', path: '/' },
      { label: '공지', path: '/notices' },
    ])
    expect(store.items).toHaveLength(2)
    expect(store.items[1].path).toBe('/notices')
  })

  it('clear는 항목을 비운다', async () => {
    const { useBreadcrumbStore } = await import('@/stores/breadcrumbStore')
    const store = useBreadcrumbStore()
    store.set([{ label: 'A', path: '/a' }])
    store.clear()
    expect(store.items).toEqual([])
  })
})
