// SPEC-CMS-DASHBOARD-PERSONALIZE-001 — DashboardPreferencePanel 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
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
  hidden_widget_instance_ids: { '1': ['w-pv-001'] },
  theme: 'LIGHT' as const,
  density: 'NORMAL' as const,
  font_scale: 1.0,
  color_palette_preference: 'DEFAULT' as const,
  sidebar_collapsed: false,
  schema_version: 1,
  updated_at: '2026-05-29T10:00:00Z',
}

describe('DashboardPreferencePanel — SPEC-CMS-DASHBOARD-PERSONALIZE-001', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  function setupStoreWith(pref = PREF_BASE) {
    const store = useDashboardPreferenceStore()
    store.preference = { ...pref }
    return store
  }

  it('마운트 시 데이터가 없으면 store.fetch 를 호출한다', async () => {
    vi.mocked(dashboardPreferenceApi.get).mockResolvedValueOnce({ data: PREF_BASE } as any)

    const store = useDashboardPreferenceStore()
    store.preference.updated_at = ''   // 비어 있음

    mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
    })

    expect(dashboardPreferenceApi.get).toHaveBeenCalled()
    expect(store).toBeDefined()
  })

  it('AC-DP-001-2: 숨김 위젯 태그를 layout 별로 렌더링한다', async () => {
    setupStoreWith(PREF_BASE)
    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
      attachTo: document.body,
    })
    // ElDrawer 는 teleport 로 body 에 렌더링되므로 DOM 반영을 위해 한 틱 대기
    await nextTick()
    // ElDrawer 는 body 에 teleport 되므로 document 에서 검색
    const list = document.querySelector('[data-testid="hidden-widget-list"]')
    expect(list?.textContent ?? '').toContain('w-pv-001')
    wrapper.unmount()
  })

  it('AC-DP-001-5: "모든 위젯 표시" 클릭 시 store.showAllWidgets(layoutId) 호출', async () => {
    setupStoreWith(PREF_BASE)
    vi.mocked(dashboardPreferenceApi.showAllWidgets).mockResolvedValueOnce({
      data: { ...PREF_BASE, hidden_widget_instance_ids: { '1': [] } },
    } as any)

    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
      attachTo: document.body,
    })
    await nextTick()
    const btn = document.querySelector('[data-testid="show-all-button"]') as HTMLButtonElement
    btn?.click()
    await Promise.resolve()
    expect(dashboardPreferenceApi.showAllWidgets).toHaveBeenCalledWith(1)
    wrapper.unmount()
  })

  it('AC-DP-002-5: "기본값으로 초기화" 클릭 시 store.reset 호출', async () => {
    setupStoreWith(PREF_BASE)
    vi.mocked(dashboardPreferenceApi.reset).mockResolvedValueOnce({ data: PREF_BASE } as any)

    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: true, layoutId: 1 },
      attachTo: document.body,
    })
    await nextTick()
    const btn = document.querySelector('[data-testid="reset-button"]') as HTMLButtonElement
    btn?.click()
    await Promise.resolve()
    expect(dashboardPreferenceApi.reset).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('modelValue=false 일 때 drawer 는 닫힘 상태를 emit 하지 않는다', () => {
    setupStoreWith(PREF_BASE)
    const wrapper = mount(DashboardPreferencePanel, {
      props: { modelValue: false, layoutId: 1 },
    })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
})
