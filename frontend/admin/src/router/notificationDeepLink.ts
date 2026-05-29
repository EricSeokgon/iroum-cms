// SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-008 — 알림 ref_type → 라우터 경로 매핑
// 매핑되지 않은 ref_type 은 null 반환 (호출 측에서 콘솔 경고 + 읽음 처리만)

export type NotificationRefType =
  | 'POST'
  | 'COMMENT'
  | 'NOTIFICATION_SEND'
  | 'INTEGRATION_LOG'
  | 'POLICY_PROGRAM'
  | (string & {})

// 1차 출시: POST / POLICY_PROGRAM 만 실제 라우트 존재.
// 나머지는 후속 SPEC 화면 도입 시점에 라우트가 등록됨 (전방 호환).
const MAPPING: Record<string, (refId: number) => string> = {
  POST:               (id) => `/board/posts/${id}/edit`,
  COMMENT:            (id) => `/board/comments/${id}`,
  NOTIFICATION_SEND:  (id) => `/notifications/send-history/${id}`,
  INTEGRATION_LOG:    (id) => `/integration/logs/${id}`,
  POLICY_PROGRAM:     (id) => `/policy/programs/${id}`,
}

/**
 * REQ-NC-008 — ref_type+ref_id → 라우터 경로 변환.
 *
 * @returns 매핑된 경로, 또는 null (매핑 미정의 또는 ref 누락)
 */
export function resolveNotificationDeepLink(
  refType: string | null | undefined,
  refId: number | null | undefined,
): string | null {
  if (!refType || refId == null) return null
  const fn = MAPPING[refType]
  if (!fn) return null
  return fn(refId)
}
