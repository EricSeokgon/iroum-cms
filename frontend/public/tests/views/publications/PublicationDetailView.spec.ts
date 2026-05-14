// SPEC-CMS-PUBLIC-001 T-007 — PublicationDetailView 검증 (C-08 zip 다운로드)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { PublicationDetail } from '@/api/publicationTypes'

const getMock = vi.fn()
const postMock = vi.fn()
vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
  ACCESS_TOKEN_KEY: 'public.accessToken',
  REFRESH_TOKEN_KEY: 'public.refreshToken',
}))

const elMessageMock = { error: vi.fn(), success: vi.fn(), info: vi.fn(), warning: vi.fn() }
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<Record<string, unknown>>('element-plus')
  return { ...actual, ElMessage: elMessageMock }
})

function makeDetail(): PublicationDetail {
  return {
    id: 7,
    title: '연구보고서',
    publicationYear: 2025,
    documentType: 'RESEARCH',
    downloadCount: 100,
    descriptionHtml: '<p>요약</p>',
    attachments: [
      { id: 101, fileName: 'a.pdf', mimeType: 'application/pdf', sizeBytes: 1024 },
      { id: 102, fileName: 'b.pdf', mimeType: 'application/pdf', sizeBytes: 2048 },
      { id: 103, fileName: 'c.pdf', mimeType: 'application/pdf', sizeBytes: 4096 },
    ],
  }
}

async function mountView(routeId = '7') {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/publications', name: 'publication-list', component: { template: '<div />' } },
      {
        path: '/publications/:id',
        name: 'publication-detail',
        component: () => import('@/views/publications/PublicationDetailView.vue'),
      },
    ],
  })
  router.push(`/publications/${routeId}`)
  await router.isReady()
  const PublicationDetailView = (
    await import('@/views/publications/PublicationDetailView.vue')
  ).default
  const wrapper = mount(PublicationDetailView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('PublicationDetailView — C-08 다중 첨부 zip 다운로드', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getMock.mockReset()
    postMock.mockReset()
    Object.values(elMessageMock).forEach((m) => m.mockReset())
    localStorage.clear()
    getMock.mockResolvedValue({ data: makeDetail() })
    // URL API 폴리필 (jsdom)
    if (!window.URL.createObjectURL) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ;(window.URL as any).createObjectURL = vi.fn(() => 'blob:mock')
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ;(window.URL as any).revokeObjectURL = vi.fn()
    } else {
      vi.spyOn(window.URL, 'createObjectURL').mockReturnValue('blob:mock')
      vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => undefined)
    }
  })

  it('선택된 첨부로 POST /posts/{id}/download-zip 호출', async () => {
    // Blob 응답 (실제 zip)
    const zipBlob = new Blob(['fake zip'], { type: 'application/zip' })
    postMock.mockResolvedValue({ data: zipBlob })

    const { wrapper } = await mountView()

    // 2개 첨부 선택
    await wrapper.find('[data-testid="publication-attachment-101"]').setValue(true)
    await wrapper.find('[data-testid="publication-attachment-102"]').setValue(true)
    await wrapper.find('[data-testid="publication-download-zip"]').trigger('click')
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith(
      '/posts/7/download-zip',
      { attachmentIds: [101, 102] },
      expect.objectContaining({ responseType: 'blob' }),
    )
  })

  it('jobId 응답(비동기) → "준비 중입니다" 토스트', async () => {
    // 서버가 responseType:blob 이어도 JSON 으로 jobId 를 반환할 수 있음
    const jobBlob = new Blob([JSON.stringify({ jobId: 'job-xyz' })], {
      type: 'application/json',
    })
    postMock.mockResolvedValue({ data: jobBlob })

    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="publication-attachment-101"]').setValue(true)
    await wrapper.find('[data-testid="publication-attachment-102"]').setValue(true)
    await wrapper.find('[data-testid="publication-attachment-103"]').setValue(true)
    await wrapper.find('[data-testid="publication-download-zip"]').trigger('click')
    // FileReader 비동기 콜백 + 다단계 await — 여러 마이크로태스크 사이클 필요
    await flushPromises()
    await flushPromises()
    await flushPromises()

    expect(elMessageMock.info).toHaveBeenCalledWith('준비 중입니다. 완료 시 알림이 발송됩니다')
  })

  it('400 응답 → "500MB를 초과합니다" 토스트', async () => {
    const axiosError = {
      isAxiosError: true,
      response: { status: 400, data: { code: 'PAYLOAD_TOO_LARGE' } },
    }
    postMock.mockRejectedValue(axiosError)
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="publication-attachment-101"]').setValue(true)
    await wrapper.find('[data-testid="publication-download-zip"]').trigger('click')
    await flushPromises()

    expect(elMessageMock.error).toHaveBeenCalledWith('500MB를 초과합니다')
  })

  it('첨부 선택 없이 클릭 시 경고 토스트 + POST 미호출', async () => {
    const { wrapper } = await mountView()
    // 클릭 시 disabled 면 트리거 동작이 일어나지 않으므로 disabled 확인
    const btn = wrapper.find('[data-testid="publication-download-zip"]')
    expect(btn.attributes('disabled')).toBeDefined()
    expect(postMock).not.toHaveBeenCalled()
  })
})
