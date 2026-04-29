// 본인 정보 API — REQ-AUTH-018 (사용자 권리: 개인정보 접근 이력 자기 조회)
// @MX:ANCHOR: [AUTO] meApi — MyPersonalDataAccessView, 테스트 mock에서 참조
// @MX:REASON: fan_in >= 3: MyPersonalDataAccessView, 라우터 가드, 테스트 mock에서 참조

import { apiClient } from '@iroum/shared/api/client'
import type { PageResponse, PersonalDataAccessEntry } from '@iroum/shared/types/api'

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
}
