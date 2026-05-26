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

// SPEC-CMS-AI-003 — RAG 자연어 질의응답 (PUBLIC, 비회원 허용)
export interface RagQueryRequest {
  question: string
}

export interface RagSource {
  id: number
  title: string
  relevance: number
}

export interface RagQueryResponse {
  answer: string
  sources: RagSource[]
  degraded: boolean
  cached: boolean
  queryRef: string
}

export type RagFeedbackValue = 'HELPFUL' | 'UNHELPFUL'

export interface RagFeedbackRequest {
  queryRef: string
  feedback: RagFeedbackValue
}

// REQ-POLICY-004 — 채널·카테고리별 알림 구독 관리
export type SubscriptionChannel = 'EMAIL' | 'KAKAO' | 'SMS' | 'INAPP'
export type SubscriptionCategory = 'POLICY_MATCH' | 'ANNOUNCEMENT' | 'REMINDER' | 'MARKETING'

export interface SubscriptionEntry {
  channel: SubscriptionChannel
  category: SubscriptionCategory
  optedIn: boolean
}

// 백엔드 PolicyProgramSummary → 프론트 PolicySummary 매핑용 내부 타입
interface RawProgramSummary {
  id: number
  code: string
  ministry: string
  programName: string
  targetIndustries: string[]
  targetRegions: string[]
  applicationStart?: string
  applicationEnd?: string
  budgetPerCompany?: number
  sourceUrl?: string
  descriptionHtml?: string
  eligibilityHtml?: string
  status: string
}

function mapProgram(p: RawProgramSummary): PolicySummary {
  return {
    id: p.id,
    title: p.programName,
    industry: p.targetIndustries?.join(', ') || '-',
    region: p.targetRegions?.length ? '전국' : '-',
    type: p.ministry ?? '정책',
    supportAmount: p.budgetPerCompany ? `${p.budgetPerCompany.toLocaleString()}원` : undefined,
    deadline: p.applicationEnd ?? undefined,
  }
}

function mapProgramDetail(p: RawProgramSummary): PolicyDetail {
  return {
    ...mapProgram(p),
    descriptionHtml: p.descriptionHtml ?? '',
    eligibilityHtml: p.eligibilityHtml ?? '',
    applyUrl: p.sourceUrl ?? undefined,
  }
}

export const policyApi = {
  async list(params: PolicyListParams = {}): Promise<PageResponse<PolicySummary>> {
    const raw = await apiClient
      .get<PageResponse<RawProgramSummary>>('/policy/programs', {
        params: { status: 'ACTIVE', ...params },
      })
      .then((r) => r.data)
    return { ...raw, content: raw.content.map(mapProgram) }
  },
  detail(id: number): Promise<PolicyDetail> {
    return apiClient
      .get<RawProgramSummary>(`/policy/programs/${id}`)
      .then((r) => mapProgramDetail(r.data))
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
  // SPEC-CMS-AI-003 — RAG 자연어 질의 (PUBLIC, ML 장애 시 degraded=true 200)
  ragQuery(req: RagQueryRequest): Promise<RagQueryResponse> {
    return apiClient
      .post<RagQueryResponse>('/ai/rag/query', req)
      .then((r) => r.data)
  },
  // SPEC-CMS-AI-003 — RAG 답변 만족도 피드백 (HELPFUL/UNHELPFUL)
  ragFeedback(req: RagFeedbackRequest): Promise<void> {
    return apiClient.post('/ai/rag/feedback', req).then(() => undefined)
  },
  // REQ-POLICY-004 — 내 알림 구독 조회 (인증 필요)
  mySubscriptions(userId: number): Promise<SubscriptionEntry[]> {
    return apiClient
      .get<SubscriptionEntry[]>('/policy/subscriptions/me', { params: { userId } })
      .then((r) => r.data)
  },
  // REQ-POLICY-004 — 내 알림 구독 일괄 업데이트 (인증 필요)
  updateSubscriptions(userId: number, entries: SubscriptionEntry[]): Promise<void> {
    return apiClient
      .put('/policy/subscriptions/me', { entries }, { params: { userId } })
      .then(() => undefined)
  },
}
