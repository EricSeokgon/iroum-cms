// SPEC-CMS-003 공지사항 — 실제 백엔드 경로: /board/masters/code/{code}, /board/posts?bbsId={id}
import { apiClient } from './client'
import type { PageResponse } from '@iroum/shared/types/api'

export interface NoticeListParams {
  page?: number
  size?: number
  keyword?: string
  categoryCode?: string
  from?: string
  to?: string
}

// 백엔드 실제 응답 타입
interface RawPostSummary {
  id: number
  bbsMasterId: number
  title: string
  authorId: number
  isNotice: boolean
  isSecret: boolean
  viewCount: number
  commentCount: number
  attachmentCount: number
  createdAt: string
}

interface RawPostDetail extends RawPostSummary {
  bbsMasterCode: string
  useComment: boolean
  contentHtml: string
  status: string
  attachments: RawAttachment[]
  updatedAt: string
}

interface RawAttachment {
  id: number
  fileName: string
  fileSize: number
  mimeType: string
}

// 프론트 표시용 정규화 타입
export interface NoticeSummary {
  id: number
  bbsId: number
  title: string
  authorUsername: string
  viewCount: number
  isNotice: boolean
  createdAt: string
}

export interface NoticeDetail extends NoticeSummary {
  useComment: boolean
  contentHtml: string
  attachments: { id: number; fileName: string; fileSize: number; mimeType: string }[]
  updatedAt: string
}

function mapSummary(p: RawPostSummary): NoticeSummary {
  return {
    id: p.id,
    bbsId: p.bbsMasterId,
    title: p.title,
    authorUsername: `작성자`,
    viewCount: p.viewCount,
    isNotice: p.isNotice,
    createdAt: p.createdAt,
  }
}

function mapDetail(p: RawPostDetail): NoticeDetail {
  return {
    ...mapSummary(p),
    useComment: p.useComment,
    contentHtml: p.contentHtml,
    attachments: p.attachments ?? [],
    updatedAt: p.updatedAt,
  }
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
  async list(params: NoticeListParams = {}): Promise<PageResponse<NoticeSummary>> {
    const boardId = await getNoticeBoardId()
    const raw = await apiClient
      .get<PageResponse<RawPostSummary>>('/board/posts', { params: { bbsId: boardId, ...params } })
      .then((r) => r.data)
    return { ...raw, content: raw.content.map(mapSummary) }
  },
  async detail(id: number): Promise<NoticeDetail> {
    const raw = await apiClient.get<RawPostDetail>(`/board/posts/${id}`).then((r) => r.data)
    return mapDetail(raw)
  },
}
