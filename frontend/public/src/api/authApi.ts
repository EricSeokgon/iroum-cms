// SPEC-CMS-002 인증
import { apiClient } from './client'
import type { LoginRequest, LoginResponse, RefreshResult } from '@iroum/shared/types/api'

export interface PublicUser {
  id: number
  uuid?: string
  username: string
  email?: string
  name?: string
  roleCodes: string[]
}

/** POST /api/v1/auth/register 요청 페이로드 */
export interface RegisterRequest {
  email: string
  password: string
  name: string
}

/** PUT /api/v1/me 요청 페이로드 */
export interface UserSelfUpdateRequest {
  email?: string
  name?: string
}

/** POST /api/v1/auth/password/change 요청 페이로드 */
export interface PasswordChangeRequest {
  currentPassword: string
  newPassword: string
}

export const authApi = {
  login(req: LoginRequest): Promise<LoginResponse> {
    return apiClient.post<LoginResponse>('/auth/login', req).then((r) => r.data)
  },
  register(req: RegisterRequest): Promise<LoginResponse> {
    return apiClient.post<LoginResponse>('/auth/register', req).then((r) => r.data)
  },
  logout(): Promise<void> {
    return apiClient.post('/auth/logout', null).then(() => undefined)
  },
  refresh(refreshToken: string): Promise<RefreshResult> {
    return apiClient.post<RefreshResult>('/auth/refresh', { refreshToken }).then((r) => r.data)
  },
  // GET /api/v1/me — MeController (AuthController의 /auth/me 와 별개)
  me(): Promise<PublicUser> {
    return apiClient.get<PublicUser>('/me').then((r) => r.data)
  },
  updateMe(req: UserSelfUpdateRequest): Promise<PublicUser> {
    return apiClient.put<PublicUser>('/me', req).then((r) => r.data)
  },
  changePassword(req: PasswordChangeRequest): Promise<void> {
    return apiClient.post('/auth/password/change', req).then(() => undefined)
  },
}
