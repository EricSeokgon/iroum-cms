// @MX:NOTE: [AUTO] 공지 다국어 번역 API — SPEC-CMS-NOTICE-I18N-001
// @MX:SPEC: SPEC-CMS-NOTICE-I18N-001 AC-NI-003, AC-NI-008

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@iroum/shared/api/client', () => ({
  apiClient: { get: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import { apiClient } from '@iroum/shared/api/client'
import { boardApi } from '@/api/board'

describe('boardApi translations (SPEC-CMS-NOTICE-I18N-001)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('AC-NI-003: PUT /board/posts/{id}/translations — 영어 번역 저장', async () => {
    ;(apiClient.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { language: 'en' } })
    await boardApi.upsertTranslation(42, { language: 'en', title: 'Test', contentHtml: '<p>Test</p>', contentText: 'Test' })
    expect(apiClient.put).toHaveBeenCalledWith('/board/posts/42/translations', expect.objectContaining({ language: 'en', title: 'Test' }))
  })

  it('AC-NI-008: DELETE /board/posts/{id}/translations/{lang} — 번역 삭제', async () => {
    ;(apiClient.delete as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })
    await boardApi.deleteTranslation(42, 'en')
    expect(apiClient.delete).toHaveBeenCalledWith('/board/posts/42/translations/en')
  })

  it('GET /board/posts/{id}/translations/{lang} — 번역 조회', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { language: 'en', title: 'English Title' } })
    const result = await boardApi.getTranslation(42, 'en')
    expect(apiClient.get).toHaveBeenCalledWith('/board/posts/42/translations/en')
    expect(result.data.title).toBe('English Title')
  })
})
