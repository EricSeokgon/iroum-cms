// 미디어 상세 화면 — Vitest 단위 테스트 (SPEC-CMS-MEDIA-001)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHashHistory } from 'vue-router'
import ko from '@/locales/ko.json'
import MediaDetailView from '@/views/media/MediaDetailView.vue'
import { mediaApi } from '@/api/media'
import type { MediaAssetDetail, MediaUsageEntry } from '@iroum/shared/types/api'

vi.mock('@/api/media', () => ({
  mediaApi: {
    get: vi.fn(),
    signedUrl: vi.fn(),
    usage: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/media', name: 'media-library', component: { template: '<div />' } },
    { path: '/media/:uuid', name: 'media-detail', component: { template: '<div />' } },
  ],
})

function makeDetail(overrides: Partial<MediaAssetDetail> = {}): MediaAssetDetail {
  return {
    uuid: 'abc-001',
    fileName: 'hero.jpg',
    mediaType: 'IMAGE',
    mimeType: 'image/jpeg',
    sizeBytes: 512000,
    thumbnailUrl: '/thumb/hero.jpg',
    altText: '히어로 이미지',
    tags: ['hero', 'banner'],
    status: 'ACTIVE',
    usageCount: 2,
    uploadedAt: '2026-04-01T08:00:00Z',
    uploadedBy: 'editor',
    description: '메인 배너 이미지',
    width: 1920,
    height: 1080,
    durationSeconds: null,
    checksum: 'sha256:abcdef1234567890',
    licenseType: 'CC_BY',
    updatedAt: '2026-04-10T12:00:00Z',
    ...overrides,
  }
}

function makeUsage(): MediaUsageEntry[] {
  return [
    { entityType: 'POST', entityId: 101, entityTitle: '공지사항 1', url: '/board/posts/101' },
  ]
}

describe('MediaDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('파일 메타데이터(파일명, 해상도, 체크섬)를 렌더링한다', async () => {
    vi.mocked(mediaApi.get).mockResolvedValueOnce({ data: makeDetail() } as never)
    vi.mocked(mediaApi.signedUrl).mockResolvedValueOnce({
      data: { signedUrl: '/signed/hero.jpg', expiresAt: '2026-04-30T00:00:00Z' },
    } as never)
    vi.mocked(mediaApi.usage).mockResolvedValueOnce({ data: makeUsage() } as never)

    const wrapper = mount(MediaDetailView, {
      props: { uuid: 'abc-001' },
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('hero.jpg')
    expect(wrapper.text()).toContain('1920')
    expect(wrapper.text()).toContain('sha256:abcdef1234567890')
  })

  it('사용처 목록을 렌더링한다', async () => {
    vi.mocked(mediaApi.get).mockResolvedValueOnce({ data: makeDetail() } as never)
    vi.mocked(mediaApi.signedUrl).mockResolvedValueOnce({
      data: { signedUrl: '/signed/hero.jpg', expiresAt: '2026-04-30T00:00:00Z' },
    } as never)
    vi.mocked(mediaApi.usage).mockResolvedValueOnce({ data: makeUsage() } as never)

    const wrapper = mount(MediaDetailView, {
      props: { uuid: 'abc-001' },
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('공지사항 1')
    expect(wrapper.text()).toContain('POST')
  })

  it('usageCount > 0이면 삭제 버튼이 비활성화된다 (사용 중 차단)', async () => {
    vi.mocked(mediaApi.get).mockResolvedValueOnce({ data: makeDetail({ usageCount: 2 }) } as never)
    vi.mocked(mediaApi.signedUrl).mockResolvedValueOnce({
      data: { signedUrl: '/signed/hero.jpg', expiresAt: '2026-04-30T00:00:00Z' },
    } as never)
    vi.mocked(mediaApi.usage).mockResolvedValueOnce({ data: makeUsage() } as never)

    const wrapper = mount(MediaDetailView, {
      props: { uuid: 'abc-001' },
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    const deleteBtn = wrapper.findAll('button').find((btn) => btn.text().includes('삭제'))
    expect(deleteBtn?.attributes('disabled')).toBeDefined()

    // 사용 중 차단 안내 메시지
    expect(wrapper.text()).toContain('사용 중인 미디어는 삭제할 수 없습니다')
  })

  it('다운로드 버튼 클릭 시 signedUrl API를 호출한다', async () => {
    vi.mocked(mediaApi.get).mockResolvedValueOnce({ data: makeDetail({ usageCount: 0 }) } as never)
    vi.mocked(mediaApi.signedUrl).mockResolvedValue({
      data: { signedUrl: 'https://cdn.example.com/signed/hero.jpg', expiresAt: '2026-04-30T00:00:00Z' },
    } as never)
    vi.mocked(mediaApi.usage).mockResolvedValueOnce({ data: [] } as never)

    // window.open mock
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

    const wrapper = mount(MediaDetailView, {
      props: { uuid: 'abc-001' },
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    const downloadBtn = wrapper.findAll('button').find((btn) => btn.text().includes('다운로드'))
    await downloadBtn!.trigger('click')
    await flushPromises()

    expect(mediaApi.signedUrl).toHaveBeenCalledWith('abc-001')
    expect(openSpy).toHaveBeenCalledWith(
      'https://cdn.example.com/signed/hero.jpg',
      '_blank',
      'noopener,noreferrer',
    )
    openSpy.mockRestore()
  })
})
