// SPEC-CMS-DASHBOARD-REFRESH-001 — 대시보드 자동 새로고침 타이머 composable
// @MX:NOTE: [AUTO] Page Visibility API 기반 타이머 일시정지/재개 + 언마운트 시 정리 보장
// @MX:SPEC: SPEC-CMS-DASHBOARD-REFRESH-001 REQ-REFRESH-002, REQ-REFRESH-003, REQ-REFRESH-004
import { ref, watch, onUnmounted, type Ref } from 'vue'

export interface DashboardAutoRefresh {
  /** 다음 새로고침까지 남은 초 (interval=null 이면 0) */
  secondsRemaining: Ref<number>
  /** 즉시 새로고침 + 카운트다운 리셋 */
  forceRefresh: () => Promise<void>
}

const TICK_MS = 1000

/**
 * 자동 새로고침 타이머 라이프사이클 관리 훅.
 *
 * - refreshIntervalSeconds 가 null 이면 타이머 미동작 (secondsRemaining=0)
 * - 1초마다 secondsRemaining 감소, 0 도달 시 onTick 호출 후 interval 로 리셋
 * - 탭이 hidden 이면 카운트다운 일시정지, visible 복귀 시 경과 시간 계산하여
 *   interval 이상 경과 시 즉시 새로고침, 미만이면 남은 시간으로 재개
 * - onUnmounted 에서 setInterval/이벤트 리스너 전부 정리 (누수 방지)
 *
 * @param refreshIntervalSeconds 새로고침 주기(초) 또는 null(꺼짐)
 * @param onTick 카운트다운 만료/강제 시 실행할 새로고침 콜백
 */
export function useDashboardAutoRefresh(
  refreshIntervalSeconds: Ref<number | null>,
  onTick: () => Promise<void>,
): DashboardAutoRefresh {
  const secondsRemaining = ref(0)

  let timer: ReturnType<typeof setInterval> | null = null
  // 동시 onTick 호출 방지 가드 — 비동기 새로고침이 겹치지 않도록 함
  let refreshing = false
  // 탭이 hidden 으로 전환된 시각(ms). visible 복귀 시 경과 시간 계산에 사용
  let hiddenAt: number | null = null

  function clearTimer(): void {
    if (timer != null) {
      clearInterval(timer)
      timer = null
    }
  }

  // onTick 을 가드와 함께 안전하게 실행
  async function runTick(): Promise<void> {
    if (refreshing) return
    refreshing = true
    try {
      await onTick()
    } finally {
      refreshing = false
    }
  }

  // 매 초 호출 — 카운트다운 감소, 0 도달 시 새로고침 후 리셋
  function onSecond(): void {
    const interval = refreshIntervalSeconds.value
    if (interval == null) return
    if (secondsRemaining.value <= 1) {
      secondsRemaining.value = interval
      void runTick()
    } else {
      secondsRemaining.value -= 1
    }
  }

  // 현재 interval 기준으로 타이머를 (재)시작
  function startTimer(): void {
    clearTimer()
    const interval = refreshIntervalSeconds.value
    if (interval == null) {
      secondsRemaining.value = 0
      return
    }
    secondsRemaining.value = interval
    timer = setInterval(onSecond, TICK_MS)
  }

  async function forceRefresh(): Promise<void> {
    await runTick()
    const interval = refreshIntervalSeconds.value
    secondsRemaining.value = interval ?? 0
    if (interval != null) {
      // 강제 새로고침 직후 카운트다운을 처음부터 다시 시작
      startTimer()
    }
  }

  // ── Page Visibility 처리 ──────────────────────────────────────────────
  function onVisibilityChange(): void {
    if (typeof document === 'undefined') return
    const interval = refreshIntervalSeconds.value
    if (interval == null) return

    if (document.visibilityState === 'hidden') {
      // 일시정지 — 타이머 정지 + 전환 시각 기록
      hiddenAt = Date.now()
      clearTimer()
      return
    }

    // visible 복귀
    if (hiddenAt != null) {
      const elapsedSec = Math.floor((Date.now() - hiddenAt) / 1000)
      hiddenAt = null
      if (elapsedSec >= secondsRemaining.value) {
        // 남은 시간 이상 경과 → 즉시 새로고침 후 재시작
        secondsRemaining.value = interval
        void runTick()
        timer = setInterval(onSecond, TICK_MS)
      } else {
        // 남은 시간만큼 차감 후 재개
        secondsRemaining.value -= elapsedSec
        timer = setInterval(onSecond, TICK_MS)
      }
    } else {
      // hiddenAt 기록 없이 visible (초기 상태) — 단순 재개
      if (timer == null) startTimer()
    }
  }

  // interval 변경 시 타이머 재시작 (null 이면 정지)
  watch(
    refreshIntervalSeconds,
    () => {
      hiddenAt = null
      startTimer()
    },
    { immediate: true },
  )

  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  onUnmounted(() => {
    clearTimer()
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  })

  return { secondsRemaining, forceRefresh }
}
