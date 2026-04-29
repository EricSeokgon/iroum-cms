// 공통 API 타입 정의
// 백엔드 ApiResponse<T> 포맷에 대응합니다

/** 서버 헬스 체크 응답 */
export interface HealthResponse {
  status: string
  service: string
  version: string
}

/** 공통 API 에러 응답 */
export interface ApiError {
  code: string
  message: string
  traceId?: string
}

/** 공통 API 응답 래퍼 */
export interface ApiResponse<T> {
  success: boolean
  data: T
  error?: ApiError
  timestamp: string
}

/** 페이지네이션 메타 */
export interface PageMeta {
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 페이지네이션 응답 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── Auth 타입 (SPEC-CMS-002) ──────────────────────────────────────────────────

/** POST /api/v1/auth/login 요청 */
export interface LoginRequest {
  username: string
  password: string
}

/** POST /api/v1/auth/login 200 응답 본문 */
export interface LoginResponse {
  accessToken: string
  expiresInSeconds: number
  tokenType: 'Bearer'
}

/** POST /api/v1/auth/refresh 200 응답 본문 */
export interface RefreshResult {
  accessToken: string
  newRefreshToken: string
  accessExpiresInSeconds: number
  refreshExpiresInSeconds: number
}

// ── 사용자 타입 (SPEC-CMS-002 REQ-AUTH-006) ──────────────────────────────────

/** 사용자 상태 코드 */
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'DELETED'

/** 사용자 목록용 요약 DTO */
export interface UserSummary {
  id: number
  uuid: string
  username: string
  email: string
  name: string
  status: UserStatus
  lastLoginAt?: string  // ISO 8601
  createdAt: string
  organizationId?: number | null
  organizationName?: string | null
}

/** 사용자 상세 DTO */
export interface UserDetail extends UserSummary {
  failCount: number
  lockedUntil?: string
  passwordChangedAt: string
  updatedAt: string
  roleCodes: string[]
}

/** 사용자 생성 요청 */
export interface UserCreateRequest {
  username: string
  email: string
  password: string
  name: string
  status?: UserStatus
  roleCodes: string[]
}

/** 사용자 수정 요청 */
export interface UserUpdateRequest {
  email?: string
  name?: string
  status?: UserStatus
  roleCodes?: string[]
}

/** 현재 로그인 사용자 정보 */
export interface UserSelf {
  id: number
  uuid: string
  username: string
  email: string
  name: string
  roleCodes: string[]
}

/** 현재 로그인 사용자 수정 요청 */
export interface UserSelfUpdateRequest {
  email?: string
  name?: string
}

// ── 비밀번호 변경 타입 (SPEC-CMS-002 REQ-AUTH-009) ──────────────────────────

/** POST /api/v1/auth/password/change 요청 */
export interface PasswordChangeRequest {
  currentPassword: string
  newPassword: string
}

/** POST /api/v1/auth/password/change 200 응답 */
export interface PasswordChangeResponse {
  message: string
}

// ── 조직 타입 (REQ-AUTH-014) ──────────────────────────────────────────────────

/** 조직 상태 코드 */
export type OrganizationStatus = 'ACTIVE' | 'INACTIVE' | 'DELETED'

/** 조직 트리 노드 (GET /api/v1/organizations/tree) */
export interface OrganizationTreeNode {
  id: number
  code: string
  name: string
  depth: number
  sortOrder: number
  status: OrganizationStatus
  children: OrganizationTreeNode[]
}

/** 조직 목록용 요약 DTO */
export interface OrganizationSummary {
  id: number
  code: string
  name: string
  parentId: number | null
  depth: number
  sortOrder: number
  status: OrganizationStatus
}

/** 조직 상세 DTO */
export interface OrganizationDetail extends OrganizationSummary {
  description?: string
  path: string
  createdAt: string
  updatedAt: string
}

/** 조직 생성 요청 */
export interface OrganizationCreateRequest {
  code: string
  name: string
  description?: string
  parentId?: number | null
  sortOrder: number
}

/** 조직 수정 요청 */
export interface OrganizationUpdateRequest {
  name?: string
  description?: string
  parentId?: number | null
  sortOrder?: number
  status?: OrganizationStatus
}

/** 조직 변경 이력 항목 */
export interface OrganizationHistoryEntry {
  id: number
  orgId: number
  version: number
  snapshot: Record<string, unknown>
  changedBy: number | null
  changedAt: string
  changeSummary?: string
}

// ── 역할 타입 (REQ-AUTH-013) ──────────────────────────────────────────────────

/** 역할 목록용 요약 DTO */
export interface RoleSummary {
  code: string
  name: string
  description?: string
  isSystem: boolean
  aliasedTo?: string | null
  userCount: number
  permissionCount: number
  createdAt: string
}

/** 역할 상세 DTO (권한 코드 목록 포함) */
export interface RoleDetail {
  code: string
  name: string
  description?: string
  isSystem: boolean
  aliasedTo?: string | null
  userCount: number
  permissionCodes: string[]
  createdAt: string
}

/** 역할 생성 요청 */
export interface RoleCreateRequest {
  /** ^[A-Z_]{3,50}$ */
  code: string
  name: string
  description?: string
  permissionCodes: string[]
}

/** 역할 수정 요청 */
export interface RoleUpdateRequest {
  name?: string
  description?: string
  permissionCodes?: string[]
}

// ── 권한 타입 (REQ-AUTH-013) ──────────────────────────────────────────────────

/** 권한 카탈로그 항목 */
export interface PermissionSummary {
  /** "USER:READ" 형식의 권한 코드 */
  code: string
  resource: string
  action: 'READ' | 'WRITE' | 'DELETE' | 'EXECUTE' | 'ADMIN'
  description?: string
}

// ── 비밀번호 재설정 (이메일 OTP) 타입 (REQ-AUTH-017) ────────────────────────

/** POST /api/v1/auth/verify/request 요청 */
export interface VerifyRequestRequest {
  channel: 'EMAIL'                  // SMS는 v0.4+ (Q-1)
  target: string
  purpose: 'SIGNUP' | 'PASSWORD_RESET' | 'IMPORTANT_CHANGE'
}

/** POST /api/v1/auth/verify/request 응답 */
export interface VerifyRequestResponse {
  requestId: string
  expiresAt: string
  cooldownSeconds: number
}

/** POST /api/v1/auth/verify/confirm 요청 */
export interface VerifyConfirmRequest {
  requestId: string
  code: string
}

/** POST /api/v1/auth/verify/confirm 응답 */
export interface VerifyConfirmResponse {
  verifiedToken: string
  purpose: string
}

/** POST /api/v1/auth/password/reset-request 요청 */
export interface PasswordResetRequestRequest {
  email: string
}

/** POST /api/v1/auth/password/reset-confirm 요청 */
export interface PasswordResetConfirmRequest {
  verifiedToken: string
  newPassword: string
}

/** 단순 메시지 응답 (reset-request, reset-confirm 등) */
export interface SimpleMessageResponse {
  message: string
}

// ── 감사 로그 타입 (REQ-AUTH-016) ─────────────────────────────────────────────

/** 권한 변경 유형 */
export type PermissionChangeType =
  | 'ROLE_ASSIGN'
  | 'ROLE_UNASSIGN'
  | 'ROLE_PERMISSION_GRANT'
  | 'ROLE_PERMISSION_REVOKE'

/** 감사 심각도 */
export type AuditSeverity = 'INFO' | 'WARN' | 'CRITICAL'

/** 권한 변경 이력 항목 */
export interface PermissionChangeEntry {
  id: number
  changeType: PermissionChangeType
  targetUserId: number | null
  targetUsername: string | null
  targetRoleCode: string | null
  targetResource: string             // role_code 또는 permission_code
  changedBy: number | null
  changedByUsername: string | null
  changedAt: string                  // ISO 8601
  severity: AuditSeverity
  reason?: string
}
