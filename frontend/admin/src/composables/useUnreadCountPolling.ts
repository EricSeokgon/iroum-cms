// SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-009 — 30초 폴링 미읽음 수 갱신 훅
// visibilityState=hidden 시 폴링 일시 중지, 복귀 시 즉시 1회 호출 + 재개
import { onBeforeUnmount, onMounted } from 'vue'
import { useNotificationCenterStore } from '@/stores/notificationCenter'

const POLL_INTERVAL_MS = 30_000

export function useUnreadCountPolling(intervalMs = POLL_INTERVAL_MS): void {
  const store = useNotificationCenterStore()
  let timer: ReturnType<typeof setInterval> | null = null

  function start(): void {
    if (timer != null) return
    timer = setInterval(() => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
        return
      }
      void store.fetchUnreadCount()
    }, intervalMs)
  }

  function stop(): void {
    if (timer != null) {
      clearInterval(timer)
      timer = null
    }
  }

  function onVisibilityChange(): void {
    if (typeof document === 'undefined') return
    if (document.visibilityState === 'visible') {
      void store.fetchUnreadCount()
    }
  }

  onMounted(() => {
    void store.fetchUnreadCount()
    start()
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisibilityChange)
    }
  })

  onBeforeUnmount(() => {
    stop()
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  })
}
