// SPEC-CMS-AUDIT-LOG-VIEW-001 — useAuditLogStore 단위 테스트 (TDD RED)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/system', () => ({
  auditLogs: {
    search: vi.fn(),
    detail: vi.fn(),
    exportCsv: vi.fn(),
    critical: vi.fn(),
  },
}))

import { useAuditLogStore } from '@/stores/auditLog'
import { auditLogs, type AuditLogResponse } from '@/api/system'

function sample(id: number, severity: AuditLogResponse['severity'] = 'INFO'): AuditLogResponse {
  return {
    id,
    event_time: '2026-05-30T09:00:00Z',
    actor_id: 100,
    actor_username: `admin${id}`,
    action: 'UPDATE',
    entity_type: 'POST',
    entity_id: String(id),
    severity,
    result: 'SUCCESS',
    before: { title: 'old' },
    after: { title: 'new' },
    ip_address: '10.0.0.1',
  }
}

describe('useAuditLogStore (SPEC-CMS-AUDIT-LOG-VIEW-001)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('AC-AL-001: fetchLogs 가 응답을 logs/total 에 채우고 loading 을 리셋한다', async () => {
    vi.mocked(auditLogs.search).mockResolvedValueOnce({
      data: { items: [sample(1), sample(2)], total: 42, page: 1, size: 50 },
    } as never)

    const store = useAuditLogStore()
    expect(store.loading).toBe(false)
    await store.fetchLogs()

    expect(store.logs).toHaveLength(2)
    expect(store.total).toBe(42)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('fetchLogs 는 현재 page/size 와 필터를 백엔드 파라미터로 전달한다', async () => {
    vi.mocked(auditLogs.search).mockResolvedValueOnce({
      data: { items: [], total: 0, page: 2, size: 100 },
    } as never)

    const store = useAuditLogStore()
    store.size = 100
    store.page = 2
    store.filters.action = ['LOGIN']
    store.filters.severity = ['CRITICAL']
    await store.fetchLogs()

    expect(auditLogs.search).toHaveBeenCalledWith(
      expect.objectContaining({ page: 2, size: 100, action: 'LOGIN', severity: 'CRITICAL' }),
    )
  })

  it('REQ-AL-003: fetchCritical 가 criticalLogs 를 채운다', async () => {
    vi.mocked(auditLogs.critical).mockResolvedValueOnce({
      data: [sample(9, 'CRITICAL')],
    } as never)

    const store = useAuditLogStore()
    await store.fetchCritical()

    expect(store.criticalLogs).toHaveLength(1)
    expect(store.criticalLogs[0].severity).toBe('CRITICAL')
  })

  it('REQ-AL-003: dismissCritical 가 세션 닫힘 플래그를 설정한다', () => {
    const store = useAuditLogStore()
    expect(store.criticalDismissed).toBe(false)
    store.dismissCritical()
    expect(store.criticalDismissed).toBe(true)
  })

  it('applyFilter 는 필터를 갱신하고 page 를 1 로 리셋한 뒤 fetchLogs 를 호출한다', async () => {
    vi.mocked(auditLogs.search).mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 50 },
    } as never)

    const store = useAuditLogStore()
    store.page = 5
    await store.applyFilter({ action: ['DELETE'], result: 'FAILURE' })

    expect(store.page).toBe(1)
    expect(store.filters.action).toEqual(['DELETE'])
    expect(store.filters.result).toBe('FAILURE')
    expect(auditLogs.search).toHaveBeenCalled()
  })

  it('AC-AL-010: resetFilter 가 기본 필터를 복원하고 fetchLogs 를 호출한다', async () => {
    vi.mocked(auditLogs.search).mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 50 },
    } as never)

    const store = useAuditLogStore()
    store.filters.action = ['CREATE']
    store.filters.severity = ['WARN']
    store.filters.result = 'SUCCESS'
    store.filters.fromTime = '2026-01-01'
    store.filters.toTime = '2026-02-01'
    store.filters.actorId = 7
    store.page = 3

    await store.resetFilter()

    expect(store.filters.action).toEqual([])
    expect(store.filters.severity).toEqual([])
    expect(store.filters.result).toBe('')
    expect(store.filters.fromTime).toBe('')
    expect(store.filters.toTime).toBe('')
    expect(store.filters.actorId).toBeNull()
    expect(store.page).toBe(1)
    expect(auditLogs.search).toHaveBeenCalled()
  })

  it('AC-AL-005: exportCsv 가 현재 필터로 export 를 호출하고 blob 다운로드를 트리거한다', async () => {
    const blob = new Blob(['id,action'], { type: 'text/csv' })
    vi.mocked(auditLogs.exportCsv).mockResolvedValueOnce({ data: blob } as never)

    const createSpy = vi.fn(() => 'blob:url')
    const revokeSpy = vi.fn()
    // @ts-expect-error jsdom 환경 보강
    globalThis.URL.createObjectURL = createSpy
    // @ts-expect-error jsdom 환경 보강
    globalThis.URL.revokeObjectURL = revokeSpy
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const store = useAuditLogStore()
    store.filters.action = ['EXPORT']
    await store.exportCsv()

    expect(auditLogs.exportCsv).toHaveBeenCalledWith(
      expect.objectContaining({ action: 'EXPORT' }),
    )
    expect(createSpy).toHaveBeenCalledWith(blob)
    expect(clickSpy).toHaveBeenCalled()
    expect(revokeSpy).toHaveBeenCalled()

    clickSpy.mockRestore()
  })

  it('REQ-AL-011: fetchLogs API 실패 시 error state 를 설정한다 (스토어는 토스트 없음)', async () => {
    vi.mocked(auditLogs.search).mockRejectedValueOnce(new Error('boom'))

    const store = useAuditLogStore()
    await store.fetchLogs()

    expect(store.error).toBe('boom')
    expect(store.loading).toBe(false)
    expect(store.logs).toEqual([])
  })

  it('fetchCritical 실패는 조용히 무시된다', async () => {
    vi.mocked(auditLogs.critical).mockRejectedValueOnce(new Error('net'))

    const store = useAuditLogStore()
    await store.fetchCritical()

    expect(store.criticalLogs).toEqual([])
    expect(store.error).toBeNull()
  })
})
