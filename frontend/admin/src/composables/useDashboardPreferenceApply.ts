// SPEC-CMS-DASHBOARD-PERSONALIZE-001 — preference → <html> data-* / CSS 변수 자동 적용
//
// 사용:
//   import { useDashboardPreferenceApply } from '@/composables/useDashboardPreferenceApply'
//   const cleanup = useDashboardPreferenceApply()   // App.vue 의 onMounted 등에서 호출
//
// 효과:
//   - preference 변경 시 <html data-theme="dark|light"> 갱신 (REQ-DP-002-1, AC-DP-002-1)
//   - <html data-density="COMPACT|NORMAL|COMFORTABLE"> 갱신 (REQ-DP-002-2)
//   - <html data-font-scale="0.875|1.0|1.125"> 갱신
//   - SYSTEM 테마 모드일 때 matchMedia 리스너 등록/해제 (AC-DP-002-2)
//
// @MX:NOTE: [AUTO] useDashboardPreferenceApply — preference 변경의 부수효과(DOM mutation) 격리
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 REQ-DP-002-1 / 002-2
import { onBeforeUnmount, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useDashboardPreferenceStore } from '@/stores/dashboardPreferenceStore'

export function useDashboardPreferenceApply(): () => void {
  const store = useDashboardPreferenceStore()
  const { preference, effectiveTheme } = storeToRefs(store)

  let mql: MediaQueryList | null = null
  let mqlHandler: ((e: MediaQueryListEvent) => void) | null = null

  function applyTheme(theme: 'light' | 'dark'): void {
    if (typeof document === 'undefined') return
    document.documentElement.dataset.theme = theme
  }

  function applyDensity(density: string): void {
    if (typeof document === 'undefined') return
    document.documentElement.dataset.density = density
  }

  function applyFontScale(scale: number): void {
    if (typeof document === 'undefined') return
    document.documentElement.dataset.fontScale = String(scale)
  }

  function disposeMql(): void {
    if (mql && mqlHandler) {
      mql.removeEventListener('change', mqlHandler)
    }
    mql = null
    mqlHandler = null
  }

  function ensureSystemListener(): void {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
    disposeMql()
    mql = window.matchMedia('(prefers-color-scheme: dark)')
    mqlHandler = (e) => {
      // SYSTEM 모드에서만 OS 변경 반영
      if (preference.value.theme === 'SYSTEM') {
        applyTheme(e.matches ? 'dark' : 'light')
      }
    }
    mql.addEventListener('change', mqlHandler)
  }

  // 즉시 1회 적용 + 변경 감시
  watch(
    effectiveTheme,
    (t) => applyTheme(t),
    { immediate: true },
  )
  watch(
    () => preference.value.density,
    (d) => applyDensity(d),
    { immediate: true },
  )
  watch(
    () => preference.value.font_scale,
    (s) => applyFontScale(s),
    { immediate: true },
  )
  watch(
    () => preference.value.theme,
    (t) => {
      if (t === 'SYSTEM') {
        ensureSystemListener()
      } else {
        disposeMql()
      }
    },
    { immediate: true },
  )

  // teardown
  function dispose(): void {
    disposeMql()
  }

  onBeforeUnmount(dispose)

  return dispose
}
