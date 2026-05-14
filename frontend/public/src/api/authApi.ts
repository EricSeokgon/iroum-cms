// SPEC-CMS-002 인증
import { apiClient } from './client'
import type { LoginRequest, LoginResponse, RefreshResult } from '@iroum/shared/types/api'

export interface PublicUser {
  id: number
  username: string
  email?: string
  name?: string
  roleCodes: string[]
}

export const authApi = {
  login(req: LoginRequest): Promise<LoginResponse> {
    return apiClient.post<LoginResponse>('/auth/login', req).then((r) => r.data)
  },
  logout(): Promise<void> {
    return apiClient.post('/auth/logout', null).then(() => undefined)
  },
  refresh(refreshToken: string): Promise<RefreshResult> {
    return apiClient.post<RefreshResult>('/auth/refresh', { refreshToken }).then((r) => r.data)
  },
  me(): Promise<PublicUser> {
    return apiClient.get<PublicUser>('/auth/me').then((r) => r.data)
  },
}
