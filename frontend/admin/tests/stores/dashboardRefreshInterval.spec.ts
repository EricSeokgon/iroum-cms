// SPEC-CMS-DASHBOARD-REFRESH-001 — refresh_interval_seconds store 확장 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/dashboardPreference', () => ({
  dashboardPreferenceApi: {
    get: vi.fn(),
    patch: vi.fn(),
    reset: vi.fn(),
    toggleVisibility: vi.fn(),
    showAllWidgets: vi.fn(),
    patchPositions: vi.fn(),
  },
}))

import { useDashboardPreferenceStore } from '@/stores/dashboardPreferenceStore'
import { dashboardPreferenceApi } from '@/api/dashboardPreference'

const PREF_DEFAULT = {
  user_id: 42,
  hidden_widget_instance_ids: {},
  theme: 'SYSTEM' as const,
  density: 'NORMAL' as const,
  font_scale: 1.0,
  color_palette_preference: 'DEFAULT' as const,
  sidebar_collapsed: false,
  refresh_interval_seconds: null,
  schema_version: 1,
  updated_at: '2026-06-02T10:00:00Z',
}

describe('dashboardPreferenceStore — refresh_interval_seconds (SPEC-CMS-DASHBOARD-REFRESH-001)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('기본값으로 refresh_interval_seconds 가 null 이다', () => {
    const store = useDashboardPreferenceStore()
    expect(store.preference.refresh_interval_seconds).toBeNull()
  })

  it('setRefreshInterval 은 has_refresh_interval_seconds=true 와 함께 PATCH 한다', async () => {
    const updated = { ...PREF_DEFAULT, refresh_interval_seconds: 60 }
    vi.mocked(dashboardPreferenceApi.patch).mockResolvedValueOnce({ data: updated } as any)

    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_DEFAULT }
    await store.setRefreshInterval(60)

    expect(dashboardPreferenceApi.patch).toHaveBeenCalledWith({
      refresh_interval_seconds: 60,
      has_refresh_interval_seconds: true,
    })
    expect(store.preference.refresh_interval_seconds).toBe(60)
  })

  it('setRefreshInterval(null) 로 자동 새로고침을 끌 수 있다', async () => {
    const updated = { ...PREF_DEFAULT, refresh_interval_seconds: null }
    vi.mocked(dashboardPreferenceApi.patch).mockResolvedValueOnce({ data: updated } as any)

    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_DEFAULT, refresh_interval_seconds: 300 }
    await store.setRefreshInterval(null)

    expect(dashboardPreferenceApi.patch).toHaveBeenCalledWith({
      refresh_interval_seconds: null,
      has_refresh_interval_seconds: true,
    })
    expect(store.preference.refresh_interval_seconds).toBeNull()
  })

  it('낙관적 갱신: PATCH 성공 전에도 refresh_interval_seconds 가 반영된다', async () => {
    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_DEFAULT, refresh_interval_seconds: null }

    // patch 를 보류시켜 낙관적 상태를 관찰
    let resolvePatch!: (v: unknown) => void
    vi.mocked(dashboardPreferenceApi.patch).mockReturnValueOnce(
      new Promise((res) => {
        resolvePatch = res
      }) as any,
    )

    const p = store.setRefreshInterval(30)
    // 낙관적 UI: 응답 전 이미 30 반영
    expect(store.preference.refresh_interval_seconds).toBe(30)

    resolvePatch({ data: { ...PREF_DEFAULT, refresh_interval_seconds: 30 } })
    await p
    expect(store.preference.refresh_interval_seconds).toBe(30)
  })

  it('PATCH 실패 시 이전 refresh_interval_seconds 로 롤백한다', async () => {
    vi.mocked(dashboardPreferenceApi.patch).mockRejectedValueOnce(new Error('500'))

    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_DEFAULT, refresh_interval_seconds: 60 }

    await expect(store.setRefreshInterval(300)).rejects.toThrow()
    expect(store.preference.refresh_interval_seconds).toBe(60)
  })
})
