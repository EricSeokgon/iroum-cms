// SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-008 — 딥링크 매핑 단위 테스트
import { describe, it, expect } from 'vitest'
import { resolveNotificationDeepLink } from '@/router/notificationDeepLink'

describe('resolveNotificationDeepLink (REQ-NC-008)', () => {
  it('POST: /board/posts/{id}/edit', () => {
    expect(resolveNotificationDeepLink('POST', 42)).toBe('/board/posts/42/edit')
  })

  it('POLICY_PROGRAM: /policy/programs/{id}', () => {
    expect(resolveNotificationDeepLink('POLICY_PROGRAM', 7)).toBe('/policy/programs/7')
  })

  it('NOTIFICATION_SEND: /notifications/send-history/{id}', () => {
    expect(resolveNotificationDeepLink('NOTIFICATION_SEND', 12)).toBe(
      '/notifications/send-history/12',
    )
  })

  it('INTEGRATION_LOG: /integration/logs/{id}', () => {
    expect(resolveNotificationDeepLink('INTEGRATION_LOG', 9)).toBe('/integration/logs/9')
  })

  it('COMMENT: /board/comments/{id}', () => {
    expect(resolveNotificationDeepLink('COMMENT', 100)).toBe('/board/comments/100')
  })

  it('ref_type 누락 시 null 반환', () => {
    expect(resolveNotificationDeepLink(null, 42)).toBeNull()
    expect(resolveNotificationDeepLink(undefined, 42)).toBeNull()
  })

  it('ref_id 누락 시 null 반환', () => {
    expect(resolveNotificationDeepLink('POST', null)).toBeNull()
    expect(resolveNotificationDeepLink('POST', undefined)).toBeNull()
  })

  it('매핑되지 않은 ref_type 시 null 반환 (AC-NC-008-3)', () => {
    expect(resolveNotificationDeepLink('UNKNOWN_TYPE', 1)).toBeNull()
  })
})
