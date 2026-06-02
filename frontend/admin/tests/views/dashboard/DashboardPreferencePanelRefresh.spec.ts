// SPEC-CMS-DASHBOARD-REFRESH-001 — 환경설정 패널 자동 새로고침 선택기 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
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

import DashboardPreferencePanel from '@/views/dashboard/DashboardPreferencePanel.vue'
import { dashboardPreferenceApi } from '@/api/dashboardPreference'
import { useDashboardPreferenceStore } from '@/stores/dashboardPreferenceStore'

const PREF_BASE = {
  user_id: 42,
  hidden_widget_instance_ids: {},
  theme: 'LIGHT' as const,
  density: 'NORMAL' as const,
  font_scale: 1.0,
  color_palette_preference: 'DEFAULT' as const,
  sidebar_collapsed: false,
  refresh_interval_seconds: null,
  schema_version: 1,
  updated_at: '2026-06-02T10:00:00Z',
}

describe('DashboardPreferencePanel 자동 새로고침 선택기 — SPEC-CMS-DASHBOARD-REFRESH-001', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 전역 setup 의 afterEach(restoreAllMocks) 가 matchMedia 목을 제거하므로,
    // Element Plus(useMediaQuery) 가 jsdom 에서 동작하도록 매 테스트마다 재설정한다.
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    })
  })

  it('자동 새로고침 옵션을 6개(끄기/30초/1분/5분/15분/30분) 구성한다', () => {
    // el-drawer body 콘텐츠는 jsdom 단위 환경에서 teleport 렌더가 비결정적이므로,
    // 컴포넌트가 expose 한 옵션 구성을 직접 검증한다.
    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_BASE }

    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
    })
    const opts = (
      wrapper.vm as unknown as {
        refreshIntervalOptions: Array<{ label: string; value: number }>
      }
    ).refreshIntervalOptions
    expect(opts).toHaveLength(6)
    expect(opts.map((o) => o.label)).toEqual(['끄기', '30초', '1분', '5분', '15분', '30분'])
    expect(opts.map((o) => o.value)).toEqual([0, 30, 60, 300, 900, 1800])
    wrapper.unmount()
  })

  it('선택기 변경 시 store.setRefreshInterval 을 호출한다 (값 그대로 전달)', () => {
    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_BASE }
    const setSpy = vi.spyOn(store, 'setRefreshInterval').mockResolvedValue(undefined)

    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
      attachTo: document.body,
    })

    ;(wrapper.vm as unknown as { onRefreshIntervalChange: (v: number) => void })
      .onRefreshIntervalChange(60)

    expect(setSpy).toHaveBeenCalledWith(60)
    wrapper.unmount()
  })

  it('"끄기"(센티넬 0) 선택 시 store.setRefreshInterval(null) 을 호출한다', () => {
    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_BASE, refresh_interval_seconds: 300 }
    const setSpy = vi.spyOn(store, 'setRefreshInterval').mockResolvedValue(undefined)

    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
      attachTo: document.body,
    })

    ;(wrapper.vm as unknown as { onRefreshIntervalChange: (v: number) => void })
      .onRefreshIntervalChange(0)

    expect(setSpy).toHaveBeenCalledWith(null)
    wrapper.unmount()
  })

  it('store.preference.refresh_interval_seconds 값이 선택기에 바인딩된다', () => {
    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_BASE, refresh_interval_seconds: 300 }

    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
      attachTo: document.body,
    })
    expect(
      (wrapper.vm as unknown as { localRefreshInterval: number }).localRefreshInterval,
    ).toBe(300)
    wrapper.unmount()
  })

  it('refresh_interval_seconds=null 이면 센티넬 0(끄기) 으로 바인딩된다', () => {
    const store = useDashboardPreferenceStore()
    store.preference = { ...PREF_BASE, refresh_interval_seconds: null }

    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
      attachTo: document.body,
    })
    expect(
      (wrapper.vm as unknown as { localRefreshInterval: number }).localRefreshInterval,
    ).toBe(0)
    wrapper.unmount()
  })
})
