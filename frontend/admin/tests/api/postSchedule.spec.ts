// @MX:NOTE: [AUTO] 게시글 예약 발행 API — SPEC-CMS-POST-SCHEDULE-001
// @MX:SPEC: SPEC-CMS-POST-SCHEDULE-001 AC-PS-001, AC-PS-006

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@iroum/shared/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import { apiClient } from '@iroum/shared/api/client'
import { boardApi } from '@/api/board'

describe('boardApi schedule (SPEC-CMS-POST-SCHEDULE-001)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('AC-PS-001: POST /board/posts/{id}/schedule — scheduledAt 본문으로 예약', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { id: 42, status: 'SCHEDULED' },
    })
    const when = '2026-12-31T09:00:00+09:00'
    const result = await boardApi.schedulePost(42, when)
    expect(apiClient.post).toHaveBeenCalledWith('/board/posts/42/schedule', { scheduledAt: when })
    expect(result.data.status).toBe('SCHEDULED')
  })

  it('AC-PS-006: DELETE /board/posts/{id}/schedule — 예약 취소(→DRAFT)', async () => {
    ;(apiClient.delete as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { id: 42, status: 'DRAFT' },
    })
    const result = await boardApi.cancelSchedule(42)
    expect(apiClient.delete).toHaveBeenCalledWith('/board/posts/42/schedule')
    expect(result.data.status).toBe('DRAFT')
  })
})
