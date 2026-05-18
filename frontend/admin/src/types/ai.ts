// AI 모델 운영 모니터링 타입 — SPEC-CMS-AI-001 Step 3
// 백엔드 Step 2 DTO와 1:1 매핑 (ApiResponse 래퍼 없는 평문 ResponseEntity<T>)

export type AiPredictionType = 'GROWTH_STAGE' | 'RISK_SCORE' | 'SIMULATION'
export type AiAggregatePeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type RetrainTriggerReason = 'DRIFT_ACCURACY' | 'DRIFT_ERROR' | 'MANUAL'
export type RetrainStatus = 'QUEUED' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'DONE' | 'CANCELED'

export interface AiMetricDto {
  id: number
  modelName: string
  predictionType: AiPredictionType
  aggregatePeriod: AiAggregatePeriod
  periodStart: string
  rmse: number | null
  mae: number | null
  accuracy: number | null
  latencyP50: number | null
  latencyP95: number | null
  latencyP99: number | null
  sampleCount: number
  driftDetected: boolean
  createdAt: string
}

export interface AiDriftAlertDto {
  id: number
  modelName: string
  predictionType: string
  driftDetected: boolean
  accuracy: number | null
  rmse: number | null
  periodStart: string
  createdAt: string
}

export interface RetrainStatusDto {
  id: number
  modelName: string
  triggerReason: RetrainTriggerReason
  triggerDetail: Record<string, unknown>
  status: RetrainStatus
  requestedAt: string
  updatedAt: string
}

export interface RetrainRequest {
  modelName: string
  triggerReason: 'MANUAL'
  triggerDetail?: Record<string, unknown>
}

export interface AiMetricQuery {
  modelName?: string
  type?: string
  from?: string
  to?: string
}

export interface ModelHealthDto {
  status: string
  loadedModels: string[]
}

export interface SimulationStatsDto {
  totalSimulations: number
  pdfStatus: Record<string, number>
}
