// 콘텐츠 관리 API 래퍼 — SPEC-CMS-004 Bundle C
import { apiClient } from '@iroum/shared/api/client'
import type { RevisionDiffResponse } from '@/types/revision'

// @MX:ANCHOR: [AUTO] contentApi — SiteView, MenuTreeView, TemplateManagerView, PageListView, PageEditorView 등에서 참조
// @MX:REASON: fan_in >= 3: Bundle C 8개 뷰 컴포넌트 및 stores/content.ts에서 공통 호출

const BASE = '/content'

// ── 공통 페이지 응답 ──────────────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── 사이트 ────────────────────────────────────────────────────────────────────
export interface SiteResponse {
  id: number
  code: string
  name: string
  domain: string
  defaultLanguage: string
  supportedLanguages: string[]
  status: 'ACTIVE' | 'INACTIVE'
  metadata?: Record<string, unknown>
}

export interface SiteUpdateRequest {
  name?: string
  domain?: string
  defaultLanguage?: string
  supportedLanguages?: string[]
  status?: 'ACTIVE' | 'INACTIVE'
}

// ── 메뉴 ─────────────────────────────────────────────────────────────────────
export interface MenuTreeNode {
  id: number
  siteId: number
  parentId: number | null
  code: string
  name: string
  url: string | null
  target: '_self' | '_blank'
  icon: string | null
  sortOrder: number
  depth: number
  path: string
  isVisible: boolean
  status: 'ACTIVE' | 'INACTIVE'
  accessible?: boolean
  children: MenuTreeNode[]
}

export interface MenuRequest {
  siteId: number
  parentId?: number | null
  code: string
  name: string
  url?: string
  target?: '_self' | '_blank'
  icon?: string
  sortOrder?: number
}

// ── 템플릿 ────────────────────────────────────────────────────────────────────
export interface TemplateResponse {
  id: number
  code: string
  name: string
  layoutType: 'FULL' | 'SIDEBAR_LEFT' | 'SIDEBAR_RIGHT' | 'LANDING' | 'BLANK'
  htmlTemplate: string
  cssAssets: string[]
  jsAssets: string[]
  description?: string
  status: 'ACTIVE' | 'INACTIVE'
  pageCount?: number
}

export interface TemplateRequest {
  code: string
  name: string
  layoutType: 'FULL' | 'SIDEBAR_LEFT' | 'SIDEBAR_RIGHT' | 'LANDING' | 'BLANK'
  htmlTemplate: string
  cssAssets?: string[]
  jsAssets?: string[]
  description?: string
}

// ── 페이지 ────────────────────────────────────────────────────────────────────
export type PageStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'RETRACTED'

/** 페이지 엔티티 응답 (PageResponse<T>와 이름 충돌 방지를 위해 별칭 사용) */
export interface PageItemResponse {
  id: number
  siteId: number
  templateId: number
  templateName?: string
  menuId: number | null
  code: string
  title: string
  slug: string
  status: PageStatus
  publishedAt: string | null
  scheduledAt: string | null
  seoTitle: string | null
  seoDescription: string | null
  seoKeywords: string | null
  ogImageUrl: string | null
  canonicalUrl: string | null
  currentVersion: number
  createdAt: string
  updatedAt: string
}

/** @deprecated PageItemResponse를 사용하세요 */
export type PageEntityResponse = PageItemResponse

export interface PageCreateRequest {
  siteId: number
  templateId: number
  menuId?: number | null
  code: string
  title: string
  slug: string
}

export interface PageUpdateRequest {
  templateId?: number
  menuId?: number | null
  title?: string
  slug?: string
  seoTitle?: string
  seoDescription?: string
  seoKeywords?: string
  ogImageUrl?: string
  canonicalUrl?: string
}

export interface PageHistoryResponse {
  id: number
  pageId: number
  version: number
  snapshot: Record<string, unknown>
  editedBy: number
  editedAt: string
  changeSummary: string | null
}

// ── 콘텐츠 블록 ───────────────────────────────────────────────────────────────
export type BlockType = 'RICH_TEXT' | 'IMAGE' | 'HTML' | 'MARKDOWN' | 'EMBED'

export interface ContentBlockResponse {
  id: number
  pageId: number
  blockType: BlockType
  sortOrder: number
  payload: Record<string, unknown>
  version: number
}

export interface ContentBlockRequest {
  blockType: BlockType
  sortOrder?: number
  payload: Record<string, unknown>
}

export interface BlockReorderItem {
  id: number
  sortOrder: number
}

// ── 팝업 ─────────────────────────────────────────────────────────────────────
export type PopupPosition = 'CENTER' | 'TOP_RIGHT' | 'BOTTOM_RIGHT' | 'TOP_LEFT' | 'BOTTOM_LEFT' | 'CUSTOM'
export type PopupTargetType = 'ALL' | 'ANONYMOUS' | 'AUTHENTICATED' | 'ROLE'

export interface PopupResponse {
  id: number
  siteId: number
  name: string
  title: string
  contentHtml: string
  position: PopupPosition
  posX: number | null
  posY: number | null
  xOffset: number | null
  yOffset: number | null
  width: number
  height?: number
  showFrom: string
  showUntil: string | null
  showTodayClose?: boolean
  displayPriority?: number
  targetType: PopupTargetType
  targetRoleCodes: string[] | null
  isActive: boolean
  status?: 'ACTIVE' | 'INACTIVE'
}

export interface PopupActiveResponse extends PopupResponse {
  cookieKey?: string
}

export interface PopupRequest {
  siteId: number
  name: string
  title?: string
  contentHtml: string
  position: PopupPosition
  posX?: number
  posY?: number
  xOffset?: number
  yOffset?: number
  width?: number
  height?: number
  showFrom: string
  showUntil?: string
  showTodayClose?: boolean
  displayPriority?: number
  targetType: PopupTargetType
  targetRoleCodes?: string[]
  isActive?: boolean
  status?: 'ACTIVE' | 'INACTIVE'
}

// ── 배너 ─────────────────────────────────────────────────────────────────────
export interface BannerResponse {
  id: number
  siteId: number
  bannerGroupCode: string
  groupCode: string       // bannerGroupCode alias
  title: string
  imageUrl: string
  linkUrl: string | null
  linkTarget: '_self' | '_blank'
  altText: string | null
  displayFrom?: string
  displayUntil?: string
  sortOrder: number
  clickCount: number
  isActive: boolean
  status?: 'ACTIVE' | 'INACTIVE'
}

export interface BannerRequest {
  siteId: number
  bannerGroupCode?: string
  groupCode?: string
  title: string
  imageUrl: string
  linkUrl?: string
  linkTarget?: '_self' | '_blank'
  altText: string
  displayFrom?: string
  displayUntil?: string
  sortOrder?: number
  isActive?: boolean
  status?: 'ACTIVE' | 'INACTIVE'
}

// ── 다국어 리소스 ─────────────────────────────────────────────────────────────
export type I18nNamespace = string   // 유연한 namespace 허용

export interface I18nResourceItem {
  namespace: I18nNamespace
  resourceId: string | number
  language?: string
  fieldName?: string
  value: string
}

// ── SEO 리다이렉트 ────────────────────────────────────────────────────────────
export interface SeoRedirectResponse {
  id: number
  siteId?: number
  fromPath: string
  toPath: string
  httpStatus: number
  isActive: boolean
  reason: string | null
  createdAt: string
}

export interface SeoRedirectRequest {
  siteId?: number
  fromPath: string
  toPath: string
  httpStatus: number
  isActive?: boolean
  reason?: string
}

// ── API 함수 그룹 ─────────────────────────────────────────────────────────────

export const sites = {
  /** GET /api/v1/content/sites/current */
  getCurrent(): Promise<{ data: SiteResponse }> {
    return apiClient.get(`${BASE}/sites/current`)
  },

  /** PUT /api/v1/content/sites/{id} */
  update(id: number, req: SiteUpdateRequest): Promise<{ data: SiteResponse }> {
    return apiClient.put(`${BASE}/sites/${id}`, req)
  },
}

export const menus = {
  /** GET /api/v1/content/menus/tree?siteId={}&context={} */
  tree(params: { siteId?: number; context?: 'ADMIN' | 'USER' }): Promise<{ data: MenuTreeNode[] }> {
    return apiClient.get(`${BASE}/menus/tree`, { params })
  },

  /** GET /api/v1/content/menus/{id} */
  get(id: number): Promise<{ data: MenuTreeNode }> {
    return apiClient.get(`${BASE}/menus/${id}`)
  },

  /** POST /api/v1/content/menus */
  create(req: MenuRequest): Promise<{ data: MenuTreeNode }> {
    return apiClient.post(`${BASE}/menus`, req)
  },

  /** PATCH /api/v1/content/menus/{id} — 이름·URL·대상 수정 */
  update(id: number, req: Partial<MenuRequest>): Promise<{ data: MenuTreeNode }> {
    return apiClient.patch(`${BASE}/menus/${id}`, req)
  },

  /** PATCH /api/v1/content/menus/{id}/order */
  changeOrder(id: number, sortOrder: number): Promise<{ data: MenuTreeNode }> {
    return apiClient.patch(`${BASE}/menus/${id}/order`, { sortOrder })
  },

  /** PATCH /api/v1/content/menus/{id}/move */
  move(id: number, parentId: number | null): Promise<{ data: MenuTreeNode }> {
    return apiClient.patch(`${BASE}/menus/${id}/move`, { parentId })
  },

  /** PATCH /api/v1/content/menus/{id}/visibility */
  toggleVisibility(id: number): Promise<{ data: MenuTreeNode }> {
    return apiClient.patch(`${BASE}/menus/${id}/visibility`)
  },

  /** DELETE /api/v1/content/menus/{id} */
  delete(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/menus/${id}`)
  },

  /** POST /api/v1/content/menus/{id}/permissions */
  replacePermissions(id: number, codes: string[]): Promise<void> {
    return apiClient.post(`${BASE}/menus/${id}/permissions`, { codes })
  },
}

export const templates = {
  /** GET /api/v1/content/templates */
  list(): Promise<{ data: TemplateResponse[] }> {
    return apiClient.get(`${BASE}/templates`)
  },

  /** POST /api/v1/content/templates */
  create(req: TemplateRequest): Promise<{ data: TemplateResponse }> {
    return apiClient.post(`${BASE}/templates`, req)
  },

  /** PUT /api/v1/content/templates/{id} */
  update(id: number, req: Partial<TemplateRequest>): Promise<{ data: TemplateResponse }> {
    return apiClient.put(`${BASE}/templates/${id}`, req)
  },

  /** PATCH /api/v1/content/templates/{id}/status */
  changeStatus(id: number, status: 'ACTIVE' | 'INACTIVE'): Promise<{ data: TemplateResponse }> {
    return apiClient.patch(`${BASE}/templates/${id}/status`, { status })
  },
}

export const pages = {
  /** GET /api/v1/content/pages?siteId=&status=&page=&size= */
  list(params: {
    siteId?: number
    status?: PageStatus | ''
    page?: number
    size?: number
    search?: string
  }): Promise<{ data: PageResponse<PageItemResponse> }> {
    return apiClient.get(`${BASE}/pages`, { params })
  },

  /** GET /api/v1/content/pages/{id} */
  get(id: number): Promise<{ data: PageItemResponse }> {
    return apiClient.get(`${BASE}/pages/${id}`)
  },

  /** GET /api/v1/content/pages/by-slug/{slug}?preview=&token= */
  bySlug(slug: string, params?: { preview?: boolean; token?: string }): Promise<{ data: PageItemResponse }> {
    return apiClient.get(`${BASE}/pages/by-slug/${slug}`, { params })
  },

  /** POST /api/v1/content/pages */
  create(req: PageCreateRequest): Promise<{ data: PageItemResponse }> {
    return apiClient.post(`${BASE}/pages`, req)
  },

  /**
   * PUT /api/v1/content/pages/{id}
   * SPEC-CMS-CONTENT-REVISION-001: 낙관적 락(expectedVersion) 적용.
   * 충돌 시 409 { code: 'REVISION_CONFLICT', currentVersion } 반환.
   */
  update(id: number, req: PageUpdateRequest, expectedVersion?: number): Promise<{ data: PageItemResponse }> {
    const body = expectedVersion != null ? { ...req, expectedVersion } : req
    return apiClient.put(`${BASE}/pages/${id}`, body)
  },

  /** PATCH /api/v1/content/pages/{id}/seo (expectedVersion 전달 시 낙관적 락 검증) */
  updateSeo(id: number, req: Partial<PageUpdateRequest>, expectedVersion?: number): Promise<{ data: PageItemResponse }> {
    const body = expectedVersion != null ? { ...req, expectedVersion } : req
    return apiClient.patch(`${BASE}/pages/${id}/seo`, body)
  },

  /** POST /api/v1/content/pages/{id}/publish */
  publish(id: number): Promise<{ data: PageItemResponse }> {
    return apiClient.post(`${BASE}/pages/${id}/publish`)
  },

  /** POST /api/v1/content/pages/{id}/schedule */
  schedule(id: number, scheduledAt: string): Promise<{ data: PageItemResponse }> {
    return apiClient.post(`${BASE}/pages/${id}/schedule`, { scheduledAt })
  },

  /** POST /api/v1/content/pages/{id}/unschedule */
  cancelSchedule(id: number): Promise<{ data: PageItemResponse }> {
    return apiClient.post(`${BASE}/pages/${id}/unschedule`)
  },

  /** POST /api/v1/content/pages/{id}/retract */
  retract(id: number, reason?: string): Promise<{ data: PageItemResponse }> {
    return apiClient.post(`${BASE}/pages/${id}/retract`, { reason })
  },

  /** GET /api/v1/content/pages/{id}/history */
  history(id: number): Promise<{ data: PageHistoryResponse[] }> {
    return apiClient.get(`${BASE}/pages/${id}/history`)
  },

  /** POST /api/v1/content/pages/{id}/rollback/{version} */
  rollback(id: number, version: number): Promise<{ data: PageItemResponse }> {
    return apiClient.post(`${BASE}/pages/${id}/rollback/${version}`)
  },

  /** GET /api/v1/content/pages/{id}/history/diff?from=&to= — 필드 단위 버전 diff (SPEC-CMS-CONTENT-REVISION-001) */
  historyDiff(id: number, from: number, to: number): Promise<{ data: RevisionDiffResponse[] }> {
    return apiClient.get(`${BASE}/pages/${id}/history/diff`, { params: { from, to } })
  },

  /** GET /api/v1/content/pages/{pageId}/blocks */
  listBlocks(pageId: number): Promise<{ data: ContentBlockResponse[] }> {
    return apiClient.get(`${BASE}/pages/${pageId}/blocks`)
  },

  /** PATCH /api/v1/content/pages/{pageId}/blocks/order */
  reorderBlocks(pageId: number, items: BlockReorderItem[]): Promise<void> {
    return apiClient.patch(`${BASE}/pages/${pageId}/blocks/order`, { items })
  },

  /** POST /api/v1/content/pages/{id}/preview-token */
  generatePreviewToken(id: number): Promise<{ data: { previewUrl: string; token: string } }> {
    return apiClient.post(`${BASE}/pages/${id}/preview-token`)
  },
}

export const blocks = {
  /** GET /api/v1/content/pages/{pageId}/blocks */
  list(pageId: number): Promise<{ data: ContentBlockResponse[] }> {
    return apiClient.get(`${BASE}/pages/${pageId}/blocks`)
  },

  /** POST /api/v1/content/pages/{pageId}/blocks */
  create(pageId: number, req: ContentBlockRequest): Promise<{ data: ContentBlockResponse }> {
    return apiClient.post(`${BASE}/pages/${pageId}/blocks`, req)
  },

  /** PUT /api/v1/content/blocks/{id} */
  update(id: number, req: Partial<ContentBlockRequest>): Promise<{ data: ContentBlockResponse }> {
    return apiClient.put(`${BASE}/blocks/${id}`, req)
  },

  /** DELETE /api/v1/content/blocks/{id} */
  delete(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/blocks/${id}`)
  },

  /** PATCH /api/v1/content/pages/{pageId}/blocks/order */
  reorder(pageId: number, items: BlockReorderItem[]): Promise<void> {
    return apiClient.patch(`${BASE}/pages/${pageId}/blocks/order`, { items })
  },
}

export const popups = {
  /** GET /api/v1/content/popups/active?siteId= */
  active(siteId: number): Promise<{ data: PopupActiveResponse[] }> {
    return apiClient.get(`${BASE}/popups/active`, { params: { siteId } })
  },

  /** GET /api/v1/content/popups?siteId= */
  list(siteId?: number): Promise<{ data: PopupResponse[] }> {
    return apiClient.get(`${BASE}/popups`, { params: siteId ? { siteId } : undefined })
  },

  /** POST /api/v1/content/popups */
  create(req: PopupRequest): Promise<{ data: PopupResponse }> {
    return apiClient.post(`${BASE}/popups`, req)
  },

  /** PUT /api/v1/content/popups/{id} */
  update(id: number, req: Partial<PopupRequest>): Promise<{ data: PopupResponse }> {
    return apiClient.put(`${BASE}/popups/${id}`, req)
  },

  /** PATCH /api/v1/content/popups/{id}/active */
  setActive(id: number, isActive: boolean): Promise<{ data: PopupResponse }> {
    return apiClient.patch(`${BASE}/popups/${id}/active`, { isActive })
  },

  /** DELETE /api/v1/content/popups/{id} */
  delete(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/popups/${id}`)
  },
}

export const banners = {
  /** GET /api/v1/content/banners/groups?siteId= */
  listGroups(siteId?: number): Promise<{ data: string[] }> {
    return apiClient.get(`${BASE}/banners/groups`, { params: siteId ? { siteId } : undefined })
  },

  /** GET /api/v1/content/banners?siteId=&groupCode= */
  list(siteId?: number, groupCode?: string): Promise<{ data: BannerResponse[] }> {
    const params: Record<string, unknown> = {}
    if (siteId) params['siteId'] = siteId
    if (groupCode) params['groupCode'] = groupCode
    return apiClient.get(`${BASE}/banners`, { params })
  },

  /** POST /api/v1/content/banners */
  create(req: BannerRequest): Promise<{ data: BannerResponse }> {
    return apiClient.post(`${BASE}/banners`, req)
  },

  /** PUT /api/v1/content/banners/{id} */
  update(id: number, req: Partial<BannerRequest>): Promise<{ data: BannerResponse }> {
    return apiClient.put(`${BASE}/banners/${id}`, req)
  },

  /** PATCH /api/v1/content/banners/{id}/active */
  setActive(id: number, isActive: boolean): Promise<{ data: BannerResponse }> {
    return apiClient.patch(`${BASE}/banners/${id}/active`, { isActive })
  },

  /** DELETE /api/v1/content/banners/{id} */
  delete(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/banners/${id}`)
  },

  /** POST /api/v1/content/banners/{id}/click */
  click(id: number): Promise<void> {
    return apiClient.post(`${BASE}/banners/${id}/click`)
  },
}

export interface I18nListResponse {
  items: I18nResourceItem[]
  total: number
  page: number
  size: number
}

export const i18n = {
  /** GET /api/v1/content/i18n/list?namespace=&page=&size= (편집기용 namespace 전체 목록) */
  listByNamespace(params: {
    namespace: I18nNamespace
    page?: number
    size?: number
  }) {
    return apiClient.get<I18nListResponse>(`${BASE}/i18n/list`, { params })
  },

  /** GET /api/v1/content/i18n?namespace=&resourceId=&lang= (단건 조회) */
  query(params: { namespace: I18nNamespace; resourceId: string | number; lang?: string }) {
    return apiClient.get<I18nResourceItem[]>(`${BASE}/i18n`, { params })
  },

  /** PUT /api/v1/content/i18n (bulk upsert) */
  bulkUpsert(items: Array<{ namespace: string; resourceId: number; language: string; fieldName: string; value: string }>) {
    return apiClient.put(`${BASE}/i18n`, { items })
  },
}

export const seoRedirects = {
  /** GET /api/v1/content/seo/redirects?siteId=&page=&size=&search= */
  list(params?: {
    siteId?: number
    page?: number
    size?: number
    search?: string
  }): Promise<{ data: SeoRedirectResponse[] }> {
    return apiClient.get(`${BASE}/seo/redirects`, { params })
  },

  /** POST /api/v1/content/seo/redirects */
  create(req: SeoRedirectRequest): Promise<{ data: SeoRedirectResponse }> {
    return apiClient.post(`${BASE}/seo/redirects`, req)
  },

  /** PUT /api/v1/content/seo/redirects/{id} */
  update(id: number, req: Partial<SeoRedirectRequest>): Promise<{ data: SeoRedirectResponse }> {
    return apiClient.put(`${BASE}/seo/redirects/${id}`, req)
  },

  /** DELETE /api/v1/content/seo/redirects/{id} */
  delete(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/seo/redirects/${id}`)
  },
}

// ── 공유 콘텐츠 블록 (SPEC-CMS-CONTENT-BLOCK-001) ──────────────────────────────
// @MX:NOTE: [AUTO] Shared* 접두사 — 페이지 스코프 ContentBlockRequest/Response/BlockType(위 라인 154-171, 독립 재사용 라이브러리)과의 이름 충돌 회피
export type SharedBlockType = 'RICH_TEXT' | 'HTML' | 'MARKDOWN' | 'EMBED'

export interface SharedContentBlockRequest {
  name: string
  slug: string
  blockType: SharedBlockType
  contentHtml?: string
  contentRaw?: string
  description?: string
  status?: string
}

export interface SharedContentBlockResponse {
  id: number
  name: string
  slug: string
  blockType: string
  contentHtml: string | null
  contentRaw: string | null
  description: string | null
  status: string
  createdAt: string
  updatedAt: string
}

// @MX:NOTE: [AUTO] contentBlockApi — ContentBlockManagerView 에서 참조 (재사용 블록 라이브러리)
export const contentBlockApi = {
  /** GET /api/v1/content/blocks */
  list(params?: { status?: string; type?: string }): Promise<{ data: SharedContentBlockResponse[] }> {
    return apiClient.get(`${BASE}/blocks`, { params })
  },

  /** GET /api/v1/content/blocks/{id} */
  getById(id: number): Promise<{ data: SharedContentBlockResponse }> {
    return apiClient.get(`${BASE}/blocks/${id}`)
  },

  /** GET /api/v1/content/blocks/{id}/preview */
  preview(id: number): Promise<{ data: { html: string } }> {
    return apiClient.get(`${BASE}/blocks/${id}/preview`)
  },

  /** POST /api/v1/content/blocks */
  create(req: SharedContentBlockRequest): Promise<{ data: SharedContentBlockResponse }> {
    return apiClient.post(`${BASE}/blocks`, req)
  },

  /** PUT /api/v1/content/blocks/{id} */
  update(id: number, req: SharedContentBlockRequest): Promise<{ data: SharedContentBlockResponse }> {
    return apiClient.put(`${BASE}/blocks/${id}`, req)
  },

  /** PATCH /api/v1/content/blocks/{id}/status */
  updateStatus(id: number, status: string): Promise<{ data: SharedContentBlockResponse }> {
    return apiClient.patch(`${BASE}/blocks/${id}/status`, { status })
  },

  /** DELETE /api/v1/content/blocks/{id} */
  delete(id: number): Promise<void> {
    return apiClient.delete(`${BASE}/blocks/${id}`)
  },
}
