// SPEC-CMS-MEDIA-001 미디어 라이브러리
import { apiClient } from './client'
import type { PageResponse, MediaAssetSummary } from '@iroum/shared/types/api'

export interface MediaListParams {
  page?: number
  size?: number
  type?: 'IMAGE' | 'VIDEO' | 'DOCUMENT' | 'AUDIO'
  collectionId?: number
  keyword?: string
}

export const mediaApi = {
  list(params: MediaListParams = {}): Promise<PageResponse<MediaAssetSummary>> {
    return apiClient
      .get<PageResponse<MediaAssetSummary>>('/media', { params })
      .then((r) => r.data)
  },
}
