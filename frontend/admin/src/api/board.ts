// 게시판 API 래퍼 — SPEC-CMS-003
import { apiClient } from '@iroum/shared/api/client'
import type {
  BbsMasterSummary,
  BbsMasterDetail,
  BbsMasterCreateRequest,
  PostSummary,
  PostDetail,
  PostCreateRequest,
  PostUpdateRequest,
  CommentSummary,
  CommentCreateRequest,
  AttachmentSummary,
  AttachmentDownloadUrl,
  PageResponse,
} from '@iroum/shared/types/api'

// @MX:ANCHOR: [AUTO] boardApi — BoardListView, PostListView, PostDetailView, PostFormView에서 참조
// @MX:REASON: fan_in >= 3: 게시판 관련 뷰 컴포넌트 및 테스트에서 공통 호출

const BASE = '/board'

// @MX:NOTE: [AUTO] 공지 다국어 번역 요청/응답 타입 — SPEC-CMS-NOTICE-I18N-001
export interface PostTranslationRequest {
  language: string
  title: string
  contentHtml?: string
  contentText?: string
}

export interface PostTranslationResponse {
  id: number
  postId: number
  language: string
  title: string
  contentHtml?: string
  contentText?: string
  updatedAt: string
}

export const boardApi = {
  // ── 게시판 마스터 ──────────────────────────────────────────────────────────

  /** GET /api/v1/board/masters */
  listMasters(): Promise<{ data: BbsMasterSummary[] }> {
    return apiClient.get(`${BASE}/masters`)
  },

  /** GET /api/v1/board/masters/{id} */
  getMaster(id: number): Promise<{ data: BbsMasterDetail }> {
    return apiClient.get(`${BASE}/masters/${id}`)
  },

  /** POST /api/v1/board/masters (SUPER_ADMIN) */
  createMaster(req: BbsMasterCreateRequest): Promise<{ data: BbsMasterDetail }> {
    return apiClient.post(`${BASE}/masters`, req)
  },

  /** PUT /api/v1/board/masters/{id} */
  updateMaster(id: number, req: Partial<BbsMasterCreateRequest>): Promise<{ data: BbsMasterDetail }> {
    return apiClient.put(`${BASE}/masters/${id}`, req)
  },

  /** DELETE /api/v1/board/masters/{id} */
  deleteMaster(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/masters/${id}`)
  },

  // ── 게시글 ──────────────────────────────────────────────────────────────────

  /** GET /api/v1/board/posts?bbsId=&page=&size=&search=&sort= */
  listPosts(params: {
    bbsId: number
    page?: number
    size?: number
    search?: string
    sort?: string
  }): Promise<{ data: PageResponse<PostSummary> }> {
    return apiClient.get(`${BASE}/posts`, { params })
  },

  /** GET /api/v1/board/posts/{id} */
  getPost(id: number): Promise<{ data: PostDetail }> {
    return apiClient.get(`${BASE}/posts/${id}`)
  },

  /** POST /api/v1/board/posts */
  createPost(bbsId: number, req: PostCreateRequest): Promise<{ data: PostDetail }> {
    return apiClient.post(`${BASE}/posts`, { ...req, bbsId })
  },

  /** PUT /api/v1/board/posts/{id} */
  updatePost(id: number, req: PostUpdateRequest): Promise<{ data: PostDetail }> {
    return apiClient.put(`${BASE}/posts/${id}`, req)
  },

  /** DELETE /api/v1/board/posts/{id} */
  deletePost(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/posts/${id}`)
  },

  // ── 게시글 예약 발행 (SPEC-CMS-POST-SCHEDULE-001) ───────────────────────────

  /** POST /api/v1/board/posts/{id}/schedule — 예약 발행 (scheduledAt: ISO-8601) */
  schedulePost(id: number, scheduledAt: string): Promise<{ data: PostDetail }> {
    return apiClient.post(`${BASE}/posts/${id}/schedule`, { scheduledAt })
  },

  /** DELETE /api/v1/board/posts/{id}/schedule — 예약 취소(→DRAFT) */
  cancelSchedule(id: number): Promise<{ data: PostDetail }> {
    return apiClient.delete(`${BASE}/posts/${id}/schedule`)
  },

  // ── 게시글 다국어 번역 (SPEC-CMS-NOTICE-I18N-001) ──────────────────────────

  /** PUT /api/v1/board/posts/{id}/translations — 번역 생성/수정 */
  upsertTranslation(postId: number, req: PostTranslationRequest): Promise<{ data: PostTranslationResponse }> {
    return apiClient.put(`${BASE}/posts/${postId}/translations`, req)
  },

  /** GET /api/v1/board/posts/{id}/translations/{lang} — 특정 언어 번역 조회 */
  getTranslation(postId: number, language: string): Promise<{ data: PostTranslationResponse }> {
    return apiClient.get(`${BASE}/posts/${postId}/translations/${language}`)
  },

  /** GET /api/v1/board/posts/{id}/translations — 번역 목록 조회 */
  listTranslations(postId: number): Promise<{ data: PostTranslationResponse[] }> {
    return apiClient.get(`${BASE}/posts/${postId}/translations`)
  },

  /** DELETE /api/v1/board/posts/{id}/translations/{lang} — 번역 삭제 */
  deleteTranslation(postId: number, language: string): Promise<void> {
    return apiClient.delete(`${BASE}/posts/${postId}/translations/${language}`)
  },

  // ── 댓글 ──────────────────────────────────────────────────────────────────

  /** GET /api/v1/board/posts/{postId}/comments */
  listComments(postId: number): Promise<{ data: CommentSummary[] }> {
    return apiClient.get(`${BASE}/posts/${postId}/comments`)
  },

  /** POST /api/v1/board/posts/{postId}/comments */
  createComment(postId: number, req: CommentCreateRequest): Promise<{ data: CommentSummary }> {
    return apiClient.post(`${BASE}/posts/${postId}/comments`, req)
  },

  /** PUT /api/v1/board/comments/{id} */
  updateComment(id: number, content: string): Promise<{ data: CommentSummary }> {
    return apiClient.put(`${BASE}/comments/${id}`, { content })
  },

  /** DELETE /api/v1/board/comments/{id} */
  deleteComment(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/comments/${id}`)
  },

  // ── 첨부파일 ──────────────────────────────────────────────────────────────

  /** POST /api/v1/board/attachments (multipart) */
  uploadAttachment(file: File): Promise<{ data: AttachmentSummary }> {
    const fd = new FormData()
    fd.append('file', file)
    return apiClient.post(`${BASE}/attachments`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** GET /api/v1/board/attachments/{id}/url */
  getAttachmentUrl(id: number): Promise<{ data: AttachmentDownloadUrl }> {
    return apiClient.get(`${BASE}/attachments/${id}/url`)
  },
}
