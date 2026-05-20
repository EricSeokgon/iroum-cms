// SPEC-CMS-003 공지사항 — 실제 백엔드 경로: /board/masters/code/{code}, /board/posts?bbsId={id}
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

// NOTICE 게시판 ID 캐시 (세션 중 재조회 방지)
let _noticeBoardId: number | null = null

async function getNoticeBoardId(): Promise<number> {
  if (_noticeBoardId) return _noticeBoardId
  const board = await apiClient
    .get<{ id: number }>('/board/masters/code/NOTICE')
    .then((r) => r.data)
  _noticeBoardId = board.id
  return board.id
}

export const noticeApi = {
  async list(params: NoticeListParams = {}): Promise<PageResponse<PostSummary>> {
    const boardId = await getNoticeBoardId()
    return apiClient
      .get<PageResponse<PostSummary>>('/board/posts', { params: { bbsId: boardId, ...params } })
      .then((r) => r.data)
  },
  detail(id: number): Promise<PostDetail> {
    return apiClient.get<PostDetail>(`/board/posts/${id}`).then((r) => r.data)
  },
}
