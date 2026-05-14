// SPEC-CMS-PUBLIC-001 T-003 — maintenanceStore 테스트
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const healthMock = vi.fn()

vi.mock('@/api/systemApi', () => ({
  systemApi: {
    health: (...args: unknown[]) => healthMock(...args),
  },
}))

describe('maintenanceStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    healthMock.mockReset()
  })

  it('초기 상태는 maintenanceMode=false', async () => {
    const { useMaintenanceStore } = await import('@/stores/maintenanceStore')
    const store = useMaintenanceStore()
    expect(store.isMaintenanceMode).toBe(false)
    expect(store.maintenanceMessage).toBe('')
  })

  it('checkMaintenance가 health API 결과로 상태를 갱신한다', async () => {
    healthMock.mockResolvedValue({
      status: 'UP',
      maintenanceMode: true,
      until: '2026-05-15T18:00:00+09:00',
      reason: '정기 점검',
    })
    const { useMaintenanceStore } = await import('@/stores/maintenanceStore')
    const store = useMaintenanceStore()
    await store.checkMaintenance()
    expect(store.isMaintenanceMode).toBe(true)
    expect(store.maintenanceMessage).toBe('정기 점검')
    expect(store.estimatedEndTime).toBe('2026-05-15T18:00:00+09:00')
  })

  it('health API 실패해도 throw 하지 않는다 (silent)', async () => {
    healthMock.mockRejectedValue(new Error('network'))
    const { useMaintenanceStore } = await import('@/stores/maintenanceStore')
    const store = useMaintenanceStore()
    await expect(store.checkMaintenance()).resolves.not.toThrow()
    expect(store.isMaintenanceMode).toBe(false)
  })
})
