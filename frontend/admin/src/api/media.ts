// 미디어 라이브러리 API 래퍼 — SPEC-CMS-MEDIA-001
import { apiClient } from '@iroum/shared/api/client'
import type {
  MediaAssetSummary,
  MediaAssetDetail,
  MediaSignedUrl,
  MediaUsageEntry,
  MediaUpdateRequest,
  MediaCollectionSummary,
  MediaType,
  PageResponse,
} from '@iroum/shared/types/api'

// @MX:ANCHOR: [AUTO] mediaApi — MediaLibraryView, MediaDetailView, MediaUploadDialog, MediaCollectionView에서 참조
// @MX:REASON: fan_in >= 3: 미디어 관련 뷰 컴포넌트 및 테스트에서 공통 호출

const BASE = '/media'

export interface MediaListParams {
  type?: MediaType | ''
  search?: string
  tags?: string
  page?: number
  size?: number
}

export const mediaApi = {
  // ── 목록 조회 ──────────────────────────────────────────────────────────────

  /** GET /api/v1/media */
  list(params?: MediaListParams): Promise<{ data: PageResponse<MediaAssetSummary> }> {
    return apiClient.get(BASE, { params })
  },

  // ── 상세 조회 ──────────────────────────────────────────────────────────────

  /** GET /api/v1/media/{uuid} */
  get(uuid: string): Promise<{ data: MediaAssetDetail }> {
    return apiClient.get(`${BASE}/${uuid}`)
  },

  // ── 업로드 ─────────────────────────────────────────────────────────────────

  /** POST /api/v1/media (multipart/form-data)
   *  @MX:WARN: [AUTO] FormData 업로드 — Content-Type은 axios가 자동 설정, 수동 설정 금지
   *  @MX:REASON: 수동으로 Content-Type을 multipart/form-data로 설정하면 boundary가 누락되어 서버에서 파싱 실패
   */
  upload(
    file: File,
    meta: { altText?: string; licenseType?: string; tags?: string[] },
    onProgress?: (percent: number) => void,
  ): Promise<{ data: MediaAssetSummary }> {
    const fd = new FormData()
    fd.append('file', file)
    if (meta.altText) fd.append('altText', meta.altText)
    if (meta.licenseType) fd.append('licenseType', meta.licenseType)
    meta.tags?.forEach((tag) => fd.append('tags', tag))

    return apiClient.post(`${BASE}/upload`, fd, {
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded * 100) / event.total))
        }
      },
    })
  },

  // ── 수정 ───────────────────────────────────────────────────────────────────

  /** PUT /api/v1/media/{uuid} */
  update(uuid: string, req: MediaUpdateRequest): Promise<{ data: MediaAssetDetail }> {
    return apiClient.put(`${BASE}/${uuid}`, req)
  },

  // ── 삭제 ───────────────────────────────────────────────────────────────────

  /** DELETE /api/v1/media/{uuid} */
  delete(uuid: string): Promise<void> {
    return apiClient.delete(`${BASE}/${uuid}`)
  },

  // ── 서명 URL ───────────────────────────────────────────────────────────────

  /** GET /api/v1/media/{uuid}/url */
  signedUrl(uuid: string): Promise<{ data: MediaSignedUrl }> {
    return apiClient.get(`${BASE}/${uuid}/url`)
  },

  // ── 사용처 조회 ────────────────────────────────────────────────────────────

  /** GET /api/v1/media/{uuid}/usage */
  usage(uuid: string): Promise<{ data: MediaUsageEntry[] }> {
    return apiClient.get(`${BASE}/${uuid}/usage`)
  },

  // ── 컬렉션 ─────────────────────────────────────────────────────────────────

  /** GET /api/v1/media/collections */
  listCollections(): Promise<{ data: MediaCollectionSummary[] }> {
    return apiClient.get(`${BASE}/collections`)
  },

  /** POST /api/v1/media/collections */
  createCollection(
    name: string,
    description?: string,
    itemUuids?: string[],
  ): Promise<{ data: MediaCollectionSummary }> {
    return apiClient.post(`${BASE}/collections`, { name, description, itemUuids })
  },
}
