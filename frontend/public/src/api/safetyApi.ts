// SPEC-CMS-006 안전관리
import { apiClient } from './client'
import type { PageResponse } from '@iroum/shared/types/api'

export interface SafetyGuidelineSummary {
  id: number
  title: string
  industryCode: string
  processCode?: string
  updatedAt: string
}

export interface SafetyGuidelineDetail extends SafetyGuidelineSummary {
  descriptionHtml: string
  checklist: Array<{ id: number; text: string; order: number }>
  relatedIncidentIds: number[]
}

export interface SafetyIncidentSummary {
  id: number
  title?: string
  industryCode: string
  incidentType?: string
  severity?: string
  occurredAt: string
  casualties?: number
  location?: string
  summary: string
  status?: string
  sourceType?: string
}

export interface SafetyListParams {
  page?: number
  size?: number
  industryCode?: string
  processCode?: string
  incidentType?: string
  severity?: string
}

export const safetyApi = {
  guidelines(params: SafetyListParams = {}): Promise<PageResponse<SafetyGuidelineSummary>> {
    return apiClient
      .get<PageResponse<SafetyGuidelineSummary>>('/safety/guidelines', { params })
      .then((r) => r.data)
  },
  guideline(id: number): Promise<SafetyGuidelineDetail> {
    return apiClient.get<SafetyGuidelineDetail>(`/safety/guidelines/${id}`).then((r) => r.data)
  },
  incidents(params: SafetyListParams = {}): Promise<PageResponse<SafetyIncidentSummary>> {
    return apiClient
      .get<PageResponse<SafetyIncidentSummary>>('/safety/incidents', { params })
      .then((r) => r.data)
  },
}
