// 미디어 라이브러리 API 래퍼 — SPEC-CMS-MEDIA-001
import axios from 'axios'
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

const BASE = '/api/v1/media'

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
    return axios.get(BASE, { params })
  },

  // ── 상세 조회 ──────────────────────────────────────────────────────────────

  /** GET /api/v1/media/{uuid} */
  get(uuid: string): Promise<{ data: MediaAssetDetail }> {
    return axios.get(`${BASE}/${uuid}`)
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
    if (meta.tags?.length) fd.append('tags', meta.tags.join(','))

    return axios.post(BASE, fd, {
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
    return axios.put(`${BASE}/${uuid}`, req)
  },

  // ── 삭제 ───────────────────────────────────────────────────────────────────

  /** DELETE /api/v1/media/{uuid} */
  delete(uuid: string): Promise<void> {
    return axios.delete(`${BASE}/${uuid}`)
  },

  // ── 서명 URL ───────────────────────────────────────────────────────────────

  /** GET /api/v1/media/{uuid}/url */
  signedUrl(uuid: string): Promise<{ data: MediaSignedUrl }> {
    return axios.get(`${BASE}/${uuid}/url`)
  },

  // ── 사용처 조회 ────────────────────────────────────────────────────────────

  /** GET /api/v1/media/{uuid}/usage */
  usage(uuid: string): Promise<{ data: MediaUsageEntry[] }> {
    return axios.get(`${BASE}/${uuid}/usage`)
  },

  // ── 컬렉션 ─────────────────────────────────────────────────────────────────

  /** GET /api/v1/media/collections */
  listCollections(): Promise<{ data: MediaCollectionSummary[] }> {
    return axios.get(`${BASE}/collections`)
  },

  /** POST /api/v1/media/collections */
  createCollection(
    name: string,
    description?: string,
    itemUuids?: string[],
  ): Promise<{ data: MediaCollectionSummary }> {
    return axios.post(`${BASE}/collections`, { name, description, itemUuids })
  },
}
