// SPEC-CMS-PUBLIC-001 T-006 — NoticeDetailView 검증 (B-04, B-05)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { PostDetail } from '@iroum/shared/types/api'

const detailMock = vi.fn()
vi.mock('@/api/noticeApi', () => ({
  noticeApi: {
    list: vi.fn(),
    detail: (...args: unknown[]) => detailMock(...args),
  },
}))

const postMock = vi.fn()
vi.mock('@/api/client', () => ({
  apiClient: {
    post: (...args: unknown[]) => postMock(...args),
  },
  ACCESS_TOKEN_KEY: 'public.accessToken',
  REFRESH_TOKEN_KEY: 'public.refreshToken',
}))

const elMessageMock = { error: vi.fn(), success: vi.fn() }
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<Record<string, unknown>>('element-plus')
  return {
    ...actual,
    ElMessage: elMessageMock,
  }
})

function sampleDetail(): PostDetail {
  return {
    id: 1,
    bbsId: 0,
    title: '안내문',
    authorUsername: 'admin',
    viewCount: 100,
    likeCount: 0,
    status: 'PUBLISHED',
    isNotice: true,
    publishedAt: '2026-04-15T09:00:00Z',
    createdAt: '2026-04-15T09:00:00Z',
    updatedAt: '2026-04-15T09:00:00Z',
    contentHtml: '<p>본문</p><script>alert(1)</script>',
    attachments: [
      { id: 11, fileName: 'a.pdf', mimeType: 'application/pdf', sizeBytes: 2048, downloadCount: 0, uploadedAt: '2026-04-15T09:00:00Z' },
    ],
  }
}

async function mountView(routeId = '1') {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/notices', name: 'notice-list', component: { template: '<div />' } },
      {
        path: '/notices/:id',
        name: 'notice-detail',
        component: () => import('@/views/notices/NoticeDetailView.vue'),
      },
    ],
  })
  router.push(`/notices/${routeId}`)
  await router.isReady()
  const NoticeDetailView = (await import('@/views/notices/NoticeDetailView.vue')).default
  const wrapper = mount(NoticeDetailView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('NoticeDetailView — B-04 본문 sanitize', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detailMock.mockReset()
    postMock.mockReset()
    elMessageMock.error.mockReset()
    localStorage.clear()
  })

  it('detail API 호출 후 본문에서 <script> 태그가 제거된다', async () => {
    detailMock.mockResolvedValue(sampleDetail())
    const { wrapper } = await mountView()
    const content = wrapper.find('[data-testid="notice-content"]')
    expect(content.exists()).toBe(true)
    expect(content.html()).toContain('본문')
    expect(content.html()).not.toContain('<script')
  })
})

describe('NoticeDetailView — B-05 첨부 다운로드 서명 URL', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detailMock.mockReset()
    postMock.mockReset()
    elMessageMock.error.mockReset()
    localStorage.clear()
    // jsdom navigation 회피
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: { href: '' },
    })
  })

  it('첨부 클릭 → POST /attachments/{id}/download-url 호출 + signedUrl 로 이동', async () => {
    detailMock.mockResolvedValue(sampleDetail())
    postMock.mockResolvedValueOnce({
      data: { signedUrl: 'https://files.example.com/abc', expiresAt: '2026-04-15T10:00:00Z' },
    })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="attachment-download"]').trigger('click')
    await flushPromises()
    expect(postMock).toHaveBeenCalledWith('/attachments/11/download-url')
    expect(window.location.href).toBe('https://files.example.com/abc')
  })

  it('403 응답 → "권한이 없습니다" 토스트', async () => {
    detailMock.mockResolvedValue(sampleDetail())
    const axiosError = {
      isAxiosError: true,
      response: { status: 403, data: { code: 'FORBIDDEN' } },
    }
    postMock.mockRejectedValueOnce(axiosError)
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="attachment-download"]').trigger('click')
    await flushPromises()
    expect(elMessageMock.error).toHaveBeenCalledWith('권한이 없습니다')
  })

  it('423 응답 → "파일 검사 중입니다" 토스트', async () => {
    detailMock.mockResolvedValue(sampleDetail())
    const axiosError = {
      isAxiosError: true,
      response: { status: 423, data: { code: 'FILE_NOT_READY' } },
    }
    postMock.mockRejectedValueOnce(axiosError)
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="attachment-download"]').trigger('click')
    await flushPromises()
    expect(elMessageMock.error).toHaveBeenCalledWith('파일 검사 중입니다')
  })
})
