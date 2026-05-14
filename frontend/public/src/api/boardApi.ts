// SPEC-CMS-003 게시판
import { apiClient } from './client'
import type { PageResponse, PostSummary, PostDetail, BbsMasterDetail } from '@iroum/shared/types/api'

export interface BoardListParams {
  page?: number
  size?: number
  keyword?: string
  category?: string
}

export const boardApi = {
  /** 게시판 마스터 단건 조회 (code 기반) */
  master(code: string): Promise<BbsMasterDetail> {
    return apiClient.get<BbsMasterDetail>('/boards', { params: { code } }).then((r) => r.data)
  },
  /** 게시글 목록 */
  posts(bbsId: number, params: BoardListParams = {}): Promise<PageResponse<PostSummary>> {
    return apiClient
      .get<PageResponse<PostSummary>>(`/boards/${bbsId}/posts`, { params })
      .then((r) => r.data)
  },
  /** 게시글 단건 */
  post(id: number): Promise<PostDetail> {
    return apiClient.get<PostDetail>(`/posts/${id}`).then((r) => r.data)
  },
}
