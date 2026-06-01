// @MX:NOTE: [AUTO] usersApi.bulkUpdateStatus — SPEC-CMS-USER-BULK-STATUS-001
// @MX:SPEC: SPEC-CMS-USER-BULK-STATUS-001 AC-UBS-003, AC-UBS-006

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@iroum/shared/api/client', () => ({
  apiClient: { patch: vi.fn() },
}))

import { apiClient } from '@iroum/shared/api/client'
import { usersApi } from '@/api/users'

describe('usersApi.bulkUpdateStatus (SPEC-CMS-USER-BULK-STATUS-001)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('AC-UBS-003: PATCH /users/bulk-status를 올바른 payload로 호출한다', async () => {
    ;(apiClient.patch as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { successCount: 3, failureCount: 0, failures: [] },
    })
    const result = await usersApi.bulkUpdateStatus([1, 2, 3], 'INACTIVE')
    expect(apiClient.patch).toHaveBeenCalledWith('/users/bulk-status', {
      userIds: [1, 2, 3],
      targetStatus: 'INACTIVE',
    })
    expect(result.data.successCount).toBe(3)
  })

  it('AC-UBS-006: failures 배열이 반환되면 그대로 전달된다', async () => {
    ;(apiClient.patch as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        successCount: 1,
        failureCount: 1,
        failures: [{ userId: 2, reason: 'DELETED 상태는 변경할 수 없습니다' }],
      },
    })
    const result = await usersApi.bulkUpdateStatus([1, 2], 'ACTIVE')
    expect(result.data.failures).toHaveLength(1)
    expect(result.data.failures[0].userId).toBe(2)
  })
})
