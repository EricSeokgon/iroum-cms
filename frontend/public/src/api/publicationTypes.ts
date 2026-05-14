// SPEC-CMS-PUBLIC-001 T-007 — 발간자료 도메인 타입
// 별도 publicationApi 모듈 없이 apiClient 를 직접 호출하는 view 에서 사용

export interface PublicationSummary {
  id: number
  title: string
  publicationYear: number
  documentType: string
  categoryId?: number
  thumbnailUrl?: string
  downloadCount: number
}

export interface PublicationAttachment {
  id: number
  fileName: string
  mimeType: string
  sizeBytes: number
}

export interface PublicationDetail extends PublicationSummary {
  descriptionHtml?: string
  attachments: PublicationAttachment[]
}

export interface PublicationListParams {
  page?: number
  size?: number
  year?: number
  documentType?: string
  categoryId?: number
  keyword?: string
}

// /posts/{id}/download-zip 응답
export interface PublicationZipBlobResponse {
  // blob 응답일 때 axios 가 data: Blob 으로 채움
  type: 'blob'
}

export interface PublicationZipJobResponse {
  jobId: string
  type: 'async'
}
