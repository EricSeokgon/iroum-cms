// 미디어 라이브러리 화면 — Vitest 단위 테스트 (SPEC-CMS-MEDIA-001)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHashHistory } from 'vue-router'
import ko from '@/locales/ko.json'
import MediaLibraryView from '@/views/media/MediaLibraryView.vue'
import { mediaApi } from '@/api/media'
import type { PageResponse, MediaAssetSummary } from '@iroum/shared/types/api'

vi.mock('@/api/media', () => ({
  mediaApi: {
    list: vi.fn(),
    delete: vi.fn(),
  },
}))

// MediaUploadDialog는 다이얼로그이므로 stub 처리
vi.mock('@/views/media/MediaUploadDialog.vue', () => ({
  default: { template: '<div data-testid="upload-dialog" />' },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/media/:uuid', name: 'media-detail', component: { template: '<div />' } },
  ],
})

function emptyPage(): PageResponse<MediaAssetSummary> {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

function makeAsset(overrides: Partial<MediaAssetSummary> = {}): MediaAssetSummary {
  return {
    uuid: 'abc-123',
    fileName: 'sample.jpg',
    mediaType: 'IMAGE',
    mimeType: 'image/jpeg',
    sizeBytes: 204800,
    thumbnailUrl: '/thumb/sample.jpg',
    altText: '샘플 이미지',
    tags: ['tag1', 'tag2'],
    status: 'ACTIVE',
    usageCount: 0,
    uploadedAt: '2026-04-01T10:00:00Z',
    uploadedBy: 'admin',
    ...overrides,
  }
}

function pageOf(items: MediaAssetSummary[]): PageResponse<MediaAssetSummary> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}

describe('MediaLibraryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('빈 상태일 때 el-empty를 렌더링한다', async () => {
    vi.mocked(mediaApi.list).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(MediaLibraryView, {
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('미디어 파일이 없습니다')
  })

  it('이미지 자산을 그리드 카드로 렌더링한다', async () => {
    const assets = [makeAsset({ fileName: 'photo.jpg', usageCount: 3 })]
    vi.mocked(mediaApi.list).mockResolvedValueOnce({ data: pageOf(assets) } as never)

    const wrapper = mount(MediaLibraryView, {
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('photo.jpg')
    // 사용 수 뱃지
    expect(wrapper.text()).toContain('3')
  })

  it('카드 버튼에 aria-label이 파일명을 포함한다 (KWCAG 4.1.2)', async () => {
    const assets = [makeAsset({ fileName: 'document.pdf', mediaType: 'DOCUMENT' })]
    vi.mocked(mediaApi.list).mockResolvedValueOnce({ data: pageOf(assets) } as never)

    const wrapper = mount(MediaLibraryView, {
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    const cardBtn = wrapper.find('button[aria-label*="document.pdf"]')
    expect(cardBtn.exists()).toBe(true)
  })

  it('타입 필터 변경 시 API 호출에 type 파라미터가 포함된다', async () => {
    vi.mocked(mediaApi.list).mockResolvedValue({ data: emptyPage() } as never)

    const wrapper = mount(MediaLibraryView, {
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    // typeFilter 내부 상태 직접 변경
    const vm = wrapper.vm as { typeFilter: string; onFilterChange: () => void }
    vm.typeFilter = 'IMAGE'
    vm.onFilterChange()
    await flushPromises()

    expect(mediaApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'IMAGE' }),
    )
  })

  it('"업로드" 버튼 클릭 시 업로드 다이얼로그가 표시된다', async () => {
    vi.mocked(mediaApi.list).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(MediaLibraryView, {
      global: { plugins: [i18n, createTestingPinia(), router] },
    })
    await flushPromises()

    const uploadBtn = wrapper.find('button.el-button--primary')
    await uploadBtn.trigger('click')
    await flushPromises()

    expect(wrapper.findComponent({ name: 'MediaUploadDialog' }).exists() ||
           wrapper.find('[data-testid="upload-dialog"]').exists()).toBe(true)
  })
})
