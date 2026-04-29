// 인증 API — REQ-AUTH-009 비밀번호 변경
// apiClient 공통 인스턴스를 사용해 admin 패키지 내 API 호출 일원화

import { apiClient } from '@iroum/shared/api/client'
import type { PasswordChangeRequest, PasswordChangeResponse } from '@iroum/shared/types/api'

// @MX:ANCHOR: [AUTO] authApi — PasswordChangeView, 향후 MFA 등 인증 관련 뷰에서 공통 사용
// @MX:REASON: fan_in >= 3 예상: PasswordChangeView, 향후 ProfileView, 테스트 코드에서 참조

export const authApi = {
  /**
   * POST /api/v1/auth/password/change
   * 성공 시 서버가 모든 refresh token 무효화 및 cookie clear
   */
  changePassword(req: PasswordChangeRequest) {
    return apiClient.post<PasswordChangeResponse>('/auth/password/change', req)
  },
}
