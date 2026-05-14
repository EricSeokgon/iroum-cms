// SPEC-CMS-003 공지사항
import { apiClient } from './client'
import type { PageResponse, PostSummary, PostDetail } from '@iroum/shared/types/api'

export interface NoticeListParams {
  page?: number
  size?: number
  keyword?: string
  categoryCode?: string
  from?: string
  to?: string
}

export const noticeApi = {
  list(params: NoticeListParams = {}): Promise<PageResponse<PostSummary>> {
    return apiClient.get<PageResponse<PostSummary>>('/notices', { params }).then((r) => r.data)
  },
  detail(id: number): Promise<PostDetail> {
    return apiClient.get<PostDetail>(`/notices/${id}`).then((r) => r.data)
  },
}
