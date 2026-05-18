// AI 운영 모니터링 공통 composable — SPEC-CMS-AI-001 Step 3
// ModelDashboard / DriftAlerts / RetrainQueue 3개 뷰의 비동기 호출 + 에러 처리 표준화
import { ElMessage } from 'element-plus'

// @MX:ANCHOR: [AUTO] useAiMonitor — ModelDashboard, DriftAlerts, RetrainQueue 3개 뷰에서 참조
// @MX:REASON: fan_in >= 3: SPEC-CMS-AI-001 3개 운영 뷰가 동일한 비동기/에러 패턴 공유

interface RunOptions {
  /** 실패 시 사용자에게 노출할 메시지 (지정 안 하면 ElMessage 미표시) */
  errorMessage?: string
}

export function useAiMonitor() {
  /**
   * 비동기 API 호출을 표준 에러 처리와 함께 실행한다.
   * 성공 시 결과를 반환, 실패 시 errorMessage 가 있으면 ElMessage.error 노출 후 null 반환.
   */
  async function run<T>(
    fn: () => Promise<{ data: T }>,
    options: RunOptions = {},
  ): Promise<T | null> {
    try {
      const res = await fn()
      return res.data
    } catch {
      if (options.errorMessage) {
        ElMessage.error(options.errorMessage)
      }
      return null
    }
  }

  return { run }
}
