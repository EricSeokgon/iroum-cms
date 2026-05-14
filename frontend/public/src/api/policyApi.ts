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
}
