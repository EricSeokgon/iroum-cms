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
