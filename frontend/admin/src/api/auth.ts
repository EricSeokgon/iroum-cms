// 인증 API — REQ-AUTH-009 비밀번호 변경, REQ-AUTH-017 비밀번호 재설정(이메일 OTP)
// apiClient 공통 인스턴스를 사용해 admin 패키지 내 API 호출 일원화

import { apiClient } from '@iroum/shared/api/client'
import type {
  PasswordChangeRequest,
  PasswordChangeResponse,
  VerifyRequestRequest,
  VerifyRequestResponse,
  VerifyConfirmRequest,
  VerifyConfirmResponse,
  PasswordResetRequestRequest,
  PasswordResetConfirmRequest,
  SimpleMessageResponse,
} from '@iroum/shared/types/api'

// @MX:ANCHOR: [AUTO] authApi — PasswordChangeView, ForgotPasswordView, 향후 MFA 뷰에서 공통 사용
// @MX:REASON: fan_in >= 3: PasswordChangeView, ForgotPasswordView, 테스트 코드에서 참조

export const authApi = {
  /**
   * POST /api/v1/auth/password/change
   * 성공 시 서버가 모든 refresh token 무효화 및 cookie clear
   */
  changePassword(req: PasswordChangeRequest) {
    return apiClient.post<PasswordChangeResponse>('/auth/password/change', req)
  },

  /**
   * POST /api/v1/auth/verify/request
   * 이메일 OTP 발송 요청 — requestId, expiresAt, cooldownSeconds 반환
   */
  verifyRequest(req: VerifyRequestRequest) {
    return apiClient.post<VerifyRequestResponse>('/auth/verify/request', req)
  },

  /**
   * POST /api/v1/auth/verify/confirm
   * OTP 코드 확인 — verifiedToken 반환 (비밀번호 재설정 시 사용)
   */
  verifyConfirm(req: VerifyConfirmRequest) {
    return apiClient.post<VerifyConfirmResponse>('/auth/verify/confirm', req)
  },

  /**
   * POST /api/v1/auth/password/reset-request
   * 비밀번호 재설정 안내 (보안 메시지만 반환, requestId 없음)
   */
  passwordResetRequest(req: PasswordResetRequestRequest) {
    return apiClient.post<SimpleMessageResponse>('/auth/password/reset-request', req)
  },

  /**
   * POST /api/v1/auth/password/reset-confirm
   * verifiedToken + newPassword로 비밀번호 재설정
   */
  passwordResetConfirm(req: PasswordResetConfirmRequest) {
    return apiClient.post<SimpleMessageResponse>('/auth/password/reset-confirm', req)
  },
}
