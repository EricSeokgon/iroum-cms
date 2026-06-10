// @MX:NOTE: [AUTO] 게시글 버전 히스토리 API — SPEC-CMS-POST-HISTORY-001
// @MX:SPEC: SPEC-CMS-POST-HISTORY-001 AC-PH-001, AC-PH-004

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@iroum/shared/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import { apiClient } from '@iroum/shared/api/client'
import { boardApi } from '@/api/board'

describe('boardApi postHistory (SPEC-CMS-POST-HISTORY-001)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('AC-PH-001: GET /board/posts/{id}/history — 페이징 파라미터로 목록 조회', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        content: [
          { id: 20, version: 2, editorName: '관리자', editReason: '오타 수정', editedAt: '2026-06-10T00:00:00Z' },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    })

    const result = await boardApi.getPostHistory(7, 0, 20)

    expect(apiClient.get).toHaveBeenCalledWith('/board/posts/7/history', {
      params: { page: 0, size: 20 },
    })
    expect(result.data.content[0].version).toBe(2)
    expect(result.data.content[0].editorName).toBe('관리자')
    expect(result.data.totalElements).toBe(1)
  })

  it('AC-PH-004: GET /board/posts/{id}/history/{version} — 단건 본문 조회', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        id: 10,
        version: 1,
        editorName: '관리자',
        editReason: '최초 작성',
        editedAt: '2026-06-09T00:00:00Z',
        title: '옛 제목',
        contentHtml: '<p>옛 본문</p>',
      },
    })

    const result = await boardApi.getPostVersion(7, 1)

    expect(apiClient.get).toHaveBeenCalledWith('/board/posts/7/history/1')
    expect(result.data.title).toBe('옛 제목')
    expect(result.data.contentHtml).toBe('<p>옛 본문</p>')
  })
})
