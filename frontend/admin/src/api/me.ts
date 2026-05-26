// 본인 정보 API — REQ-AUTH-018 (사용자 권리: 개인정보 접근 이력 자기 조회), REQ-AUTH-011
// @MX:ANCHOR: [AUTO] meApi — MyPersonalDataAccessView, MyLoginHistoryView, NotificationSettingsView, 테스트 mock에서 참조
// @MX:REASON: fan_in >= 3: MyPersonalDataAccessView, MyLoginHistoryView, NotificationSettingsView, AdminLayout 라우터 명령, 테스트 mock에서 참조

import { apiClient } from '@iroum/shared/api/client'
import type { PageResponse, PersonalDataAccessEntry, LoginHistoryEntry } from '@iroum/shared/types/api'

export const meApi = {
  /**
   * 본인 개인정보 접근 이력 조회
   * GET /api/v1/me/personal-data-access
   * 권한: 인증된 모든 사용자 (자기 데이터)
   */
  myPersonalDataAccess(params: { page?: number; size?: number }) {
    return apiClient.get<PageResponse<PersonalDataAccessEntry>>(
      '/me/personal-data-access',
      { params },
    )
  },

  /**
   * 본인 로그인 이력 조회
   * GET /api/v1/me/login-history
   * 권한: 인증된 모든 사용자 (자기 데이터)
   * REQ-AUTH-011
   */
  myLoginHistory(params: { page?: number; size?: number }) {
    return apiClient.get<PageResponse<LoginHistoryEntry>>(
      '/me/login-history',
      { params },
    )
  },

  /**
   * Q&A 답변 알림 이메일 수신 설정 조회
   * GET /api/v1/me/notifications/preferences
   * 권한: 인증된 모든 사용자 (자기 설정)
   */
  getQnaNotificationPreference(): Promise<{ qnaAnswer: { email: boolean } }> {
    return apiClient.get<{ qnaAnswer: { email: boolean } }>(
      '/me/notifications/preferences',
    ).then((r) => r.data)
  },

  /**
   * Q&A 답변 알림 이메일 수신 설정 변경
   * PUT /api/v1/me/notifications/preferences
   * 권한: 인증된 모든 사용자 (자기 설정)
   * 응답: 204 No Content
   */
  updateQnaNotificationPreference(email: boolean): Promise<void> {
    return apiClient.put<void>('/me/notifications/preferences', {
      qnaAnswer: { email },
    }).then(() => {})
  },
}
