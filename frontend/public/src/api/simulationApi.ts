// SPEC-CMS-SIM-001 — 공개 창업 시뮬레이션 (비회원 허용)
// 3단계 위저드: 입력 → 결과 → PDF 다운로드
import { apiClient } from './client'

// 시뮬레이션 시작 요청 — 업종/자본/설립연도/매출 등 기업 프로필
export interface SimulationStartRequest {
  ksicCode: string // 5자 업종코드 (KSIC)
  capitalAmount: number // 자본금 (원)
  foundingYear: number // 설립연도
  revenueAmount: number // 예상 매출 (원)
  employeeCount?: number // 직원 수 (선택)
  horizonYears: 3 | 5 // 투영 기간 (기본 3)
}

// 시뮬레이션 결과 — projectionResult/recommendedPolicies는 JSON 문자열
export interface SimulationResult {
  sessionId: string
  pdfStatus: string // 'NONE' | 'GENERATING' | 'READY' | 'FAILED'
  projectionResult: string // JSON 문자열 — { "projection": [...] }
  horizonApplied: number
  recommendedPolicies: string | null
}

export const simulationApi = {
  // 시뮬레이션 시작 — POST /ai/simulation
  start(req: SimulationStartRequest): Promise<SimulationResult> {
    return apiClient.post<SimulationResult>('/ai/simulation', req).then((r) => r.data)
  },
  // 기존 세션 결과 조회 — GET /ai/simulation/{sessionId}
  getResult(sessionId: string): Promise<SimulationResult> {
    return apiClient
      .get<SimulationResult>(`/ai/simulation/${sessionId}`)
      .then((r) => r.data)
  },
  // PDF 생성·다운로드 — POST /ai/simulation/{sessionId}/pdf (Blob 응답)
  generatePdf(sessionId: string): Promise<Blob> {
    return apiClient
      .post<Blob>(`/ai/simulation/${sessionId}/pdf`, null, { responseType: 'blob' })
      .then((r) => r.data)
  },
}
