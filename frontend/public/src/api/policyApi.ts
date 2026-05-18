// SPEC-CMS-007 정책사업
import { apiClient } from './client'
import type { PageResponse } from '@iroum/shared/types/api'

export interface PolicySummary {
  id: number
  title: string
  industry: string
  region: string
  type: string
  supportAmount?: string
  deadline?: string
}

export interface PolicyDetail extends PolicySummary {
  descriptionHtml: string
  eligibilityHtml: string
  applyUrl?: string
  contact?: string
}

export interface PolicyMatchRequest {
  industry?: string
  capitalAmount?: number
  revenueAmount?: number
  employeeCount?: number
  region?: string
}

export interface PolicyMatchResult {
  policyId: number
  score: number
  reason: string
  policy: PolicySummary
}

export interface PolicyListParams {
  page?: number
  size?: number
  industry?: string
  region?: string
  type?: string
  keyword?: string
}

// SPEC-CMS-AI-002 — AI 하이브리드 정책 추천 (규칙 + 시맨틱)
export interface AiPolicyMatchRequest {
  companyProfile: Record<string, unknown>
  queryText?: string
  topK?: number
}

export interface AiPolicyMatchExplanation {
  ruleBreakdown: Record<string, unknown>
  matchedTerms: string[]
  rationale: string
  semanticAvailable: boolean
}

export interface AiPolicyMatchItem {
  policyId: number
  hybridScore: number
  ruleScore: number
  semanticScore: number
  explanation: AiPolicyMatchExplanation
}

export interface AiPolicyMatchResponse {
  items: AiPolicyMatchItem[]
  degraded: boolean
}

export type PolicyFeedbackType = 'CLICKED' | 'APPLIED' | 'DISMISSED'

export interface PolicyFeedbackRequest {
  sessionRef: string
  interactionType: PolicyFeedbackType
  policyId: number
}

export const policyApi = {
  list(params: PolicyListParams = {}): Promise<PageResponse<PolicySummary>> {
    return apiClient.get<PageResponse<PolicySummary>>('/policies', { params }).then((r) => r.data)
  },
  detail(id: number): Promise<PolicyDetail> {
    return apiClient.get<PolicyDetail>(`/policies/${id}`).then((r) => r.data)
  },
  match(req: PolicyMatchRequest): Promise<PolicyMatchResult[]> {
    return apiClient.post<PolicyMatchResult[]>('/policies/match', req).then((r) => r.data)
  },
  // SPEC-CMS-AI-002 — AI 하이브리드 추천 (PUBLIC, 비회원 허용)
  aiMatch(req: AiPolicyMatchRequest): Promise<AiPolicyMatchResponse> {
    return apiClient
      .post<AiPolicyMatchResponse>('/ai/policy-match', req)
      .then((r) => r.data)
  },
  // SPEC-CMS-AI-002 — 추천 상호작용 피드백 (CLICKED/APPLIED/DISMISSED)
  sendFeedback(req: PolicyFeedbackRequest): Promise<void> {
    return apiClient.post('/ai/policy-match/feedback', req).then(() => undefined)
  },
}
