// SPEC-CMS-DASHBOARD-PERSONALIZE-001 — 사용자별 환경설정 store 단위 테스트
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
  schema_version: 1,
  updated_at: '2026-05-29T10:00:00Z',
}

describe('useDashboardPreferenceStore — SPEC-CMS-DASHBOARD-PERSONALIZE-001', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  // ── fetch ─────────────────────────────────────────────────────────────────
  describe('fetch (AC-DP-API-1)', () => {
    it('성공 시 preference 상태를 채운다', async () => {
      vi.mocked(dashboardPreferenceApi.get).mockResolvedValueOnce({ data: PREF_DEFAULT } as any)

      const store = useDashboardPreferenceStore()
      await store.fetch()

      expect(store.preference).toEqual(PREF_DEFAULT)
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
    })

    it('실패 시 error 를 채우고 loading 을 false 로 복원한다', async () => {
      vi.mocked(dashboardPreferenceApi.get).mockRejectedValueOnce(new Error('network'))

      const store = useDashboardPreferenceStore()
      await store.fetch()

      expect(store.error).toBe('network')
      expect(store.loading).toBe(false)
    })
  })

  // ── update (AC-DP-002-6 롤백) ─────────────────────────────────────────────
  describe('update (AC-DP-API-2, AC-DP-002-6)', () => {
    it('성공 시 응답으로 preference 를 교체한다', async () => {
      const updated = { ...PREF_DEFAULT, theme: 'DARK' as const }
      vi.mocked(dashboardPreferenceApi.patch).mockResolvedValueOnce({ data: updated } as any)

      const store = useDashboardPreferenceStore()
      store.preference = { ...PREF_DEFAULT }
      await store.update({ theme: 'DARK' })

      expect(store.preference.theme).toBe('DARK')
    })

    it('실패 시 이전 값으로 롤백한다 (AC-DP-002-6)', async () => {
      vi.mocked(dashboardPreferenceApi.patch).mockRejectedValueOnce(new Error('500'))

      const store = useDashboardPreferenceStore()
      store.preference = { ...PREF_DEFAULT, theme: 'LIGHT' as const }

      await expect(store.update({ theme: 'DARK' })).rejects.toThrow()

      expect(store.preference.theme).toBe('LIGHT')
      expect(store.error).toBe('500')
    })
  })

  // ── reset (AC-DP-002-5) ──────────────────────────────────────────────────
  describe('reset (AC-DP-002-5)', () => {
    it('reset 후 응답 값으로 preference 를 교체한다 (서버가 hidden 보존을 보장)', async () => {
      const resetResp = {
        ...PREF_DEFAULT,
        theme: 'SYSTEM' as const,
        density: 'NORMAL' as const,
        font_scale: 1.0,
        color_palette_preference: 'DEFAULT' as const,
        hidden_widget_instance_ids: { '1': ['w-a'] },
      }
      vi.mocked(dashboardPreferenceApi.reset).mockResolvedValueOnce({ data: resetResp } as any)

      const store = useDashboardPreferenceStore()
      await store.reset()

      expect(store.preference.theme).toBe('SYSTEM')
      expect(store.preference.hidden_widget_instance_ids).toEqual({ '1': ['w-a'] })
    })
  })

  // ── toggleVisibility (AC-DP-001-1/2) ─────────────────────────────────────
  describe('toggleVisibility', () => {
    it('AC-DP-001-1: hidden=true 호출 후 응답의 hidden 맵을 반영한다', async () => {
      const updated = {
        ...PREF_DEFAULT,
        hidden_widget_instance_ids: { '12': ['w-pv-001'] },
      }
      vi.mocked(dashboardPreferenceApi.toggleVisibility).mockResolvedValueOnce({
        data: updated,
      } as any)

      const store = useDashboardPreferenceStore()
      await store.toggleVisibility(12, 'w-pv-001', true)

      expect(store.isHidden(12, 'w-pv-001')).toBe(true)
      expect(dashboardPreferenceApi.toggleVisibility).toHaveBeenCalledWith(12, {
        instance_id: 'w-pv-001',
        hidden: true,
      })
    })

    it('AC-DP-001-2: hidden=false 호출 후 isHidden 이 false 로 복귀한다', async () => {
      vi.mocked(dashboardPreferenceApi.toggleVisibility).mockResolvedValueOnce({
        data: { ...PREF_DEFAULT, hidden_widget_instance_ids: { '12': [] } },
      } as any)

      const store = useDashboardPreferenceStore()
      store.preference.hidden_widget_instance_ids = { '12': ['w-pv-001'] }

      await store.toggleVisibility(12, 'w-pv-001', false)

      expect(store.isHidden(12, 'w-pv-001')).toBe(false)
    })
  })

  // ── showAllWidgets (AC-DP-001-5) ─────────────────────────────────────────
  describe('showAllWidgets (AC-DP-001-5)', () => {
    it('특정 layout 의 hidden 배열을 비운다', async () => {
      vi.mocked(dashboardPreferenceApi.showAllWidgets).mockResolvedValueOnce({
        data: { ...PREF_DEFAULT, hidden_widget_instance_ids: { '5': [] } },
      } as any)

      const store = useDashboardPreferenceStore()
      await store.showAllWidgets(5)

      expect(store.preference.hidden_widget_instance_ids['5']).toEqual([])
    })
  })

  // ── effectiveTheme (AC-DP-002-2 SYSTEM) ──────────────────────────────────
  describe('effectiveTheme — AC-DP-002-2 SYSTEM', () => {
    it('theme=LIGHT 이면 light 반환', () => {
      const store = useDashboardPreferenceStore()
      store.preference.theme = 'LIGHT'
      expect(store.effectiveTheme).toBe('light')
    })

    it('theme=DARK 이면 dark 반환', () => {
      const store = useDashboardPreferenceStore()
      store.preference.theme = 'DARK'
      expect(store.effectiveTheme).toBe('dark')
    })

    it('theme=SYSTEM 이면 matchMedia 결과를 따른다 (matches=true → dark)', () => {
      const matchMediaMock = vi.fn().mockReturnValue({
        matches: true,
        media: '(prefers-color-scheme: dark)',
        addEventListener: () => {},
        removeEventListener: () => {},
      })
      Object.defineProperty(window, 'matchMedia', {
        value: matchMediaMock,
        configurable: true,
        writable: true,
      })

      const store = useDashboardPreferenceStore()
      store.preference.theme = 'SYSTEM'

      expect(store.effectiveTheme).toBe('dark')
    })
  })

  // ── patchPositions (AC-DP-003-1) ─────────────────────────────────────────
  describe('patchPositions (AC-DP-003-1)', () => {
    it('entries 와 expected_updated_at 을 API 에 그대로 위임한다', async () => {
      vi.mocked(dashboardPreferenceApi.patchPositions).mockResolvedValueOnce({ data: undefined } as any)

      const store = useDashboardPreferenceStore()
      await store.patchPositions(
        1,
        [{ instance_id: 'w-a', position: { x: 6, y: 0, w: 6, h: 4 } }],
        { expectedUpdatedAt: '2026-05-29T10:00:00Z' },
      )

      expect(dashboardPreferenceApi.patchPositions).toHaveBeenCalledWith(1, {
        entries: [{ instance_id: 'w-a', position: { x: 6, y: 0, w: 6, h: 4 } }],
        expected_updated_at: '2026-05-29T10:00:00Z',
      })
    })

    it('AC-DP-003-5: 서버가 409 응답 → 에러 전파 + error 설정', async () => {
      vi.mocked(dashboardPreferenceApi.patchPositions).mockRejectedValueOnce(new Error('Conflict'))

      const store = useDashboardPreferenceStore()
      await expect(
        store.patchPositions(1, [{ instance_id: 'w-a', position: { x: 0, y: 0, w: 6, h: 4 } }]),
      ).rejects.toThrow('Conflict')
      expect(store.error).toBe('Conflict')
    })
  })
})
