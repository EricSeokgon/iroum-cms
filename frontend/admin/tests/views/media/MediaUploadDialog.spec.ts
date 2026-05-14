// 미디어 업로드 다이얼로그 — Vitest 단위 테스트 (SPEC-CMS-MEDIA-001)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import MediaUploadDialog from '@/views/media/MediaUploadDialog.vue'
import { mediaApi } from '@/api/media'
import type { MediaAssetSummary } from '@iroum/shared/types/api'

vi.mock('@/api/media', () => ({
  mediaApi: {
    upload: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function makeUploadedAsset(): MediaAssetSummary {
  return {
    uuid: 'def-456',
    fileName: 'test.png',
    mediaType: 'IMAGE',
    mimeType: 'image/png',
    sizeBytes: 10240,
    thumbnailUrl: null,
    altText: '테스트',
    tags: [],
    status: 'ACTIVE',
    usageCount: 0,
    uploadedAt: '2026-04-29T09:00:00Z',
    uploadedBy: 'admin',
  }
}

describe('MediaUploadDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('다이얼로그가 렌더링된다', () => {
    const wrapper = mount(MediaUploadDialog, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    expect(wrapper.find('.el-dialog').exists() ||
           wrapper.find('[role="dialog"]').exists() ||
           wrapper.text().includes('미디어 업로드')).toBe(true)
  })

  it('이미지 파일이 있을 때 alt_text 필수 안내 메시지가 노출된다', async () => {
    const wrapper = mount(MediaUploadDialog, {
      global: { plugins: [i18n, createTestingPinia()] },
    })

    // hasImageFile 상태를 강제 설정하여 조건부 메시지 확인
    const vm = wrapper.vm as { fileList: Array<{ name: string; raw: File }>; hasImageFile: boolean }
    const fakeFile = new File(['data'], 'photo.jpg', { type: 'image/jpeg' })
    vm.fileList = [{ name: 'photo.jpg', raw: fakeFile } as never]
    await flushPromises()

    // 이미지 파일 포함 시 alt text 필수 안내
    expect(wrapper.text()).toContain('대체 텍스트')
  })

  it('업로드 성공 시 uploaded 이벤트가 발생한다', async () => {
    vi.mocked(mediaApi.upload).mockResolvedValueOnce({ data: makeUploadedAsset() } as never)

    const wrapper = mount(MediaUploadDialog, {
      global: { plugins: [i18n, createTestingPinia()] },
    })

    const fakeFile = new File(['data'], 'photo.jpg', { type: 'image/jpeg' })
    const vm = wrapper.vm as {
      fileList: unknown[]
      startUpload: () => Promise<void>
    }
    vm.fileList = [{ name: 'photo.jpg', raw: fakeFile, uid: 1, status: 'ready' }] as never

    await vm.startUpload()
    await flushPromises()

    expect(wrapper.emitted('uploaded')).toBeTruthy()
  })

  it('"취소" 버튼 클릭 시 close 이벤트가 발생한다', async () => {
    const wrapper = mount(MediaUploadDialog, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    // 취소 버튼 찾기
    const cancelBtn = wrapper.findAll('button').find((btn) =>
      btn.text().includes('취소'),
    )
    expect(cancelBtn).toBeDefined()
    await cancelBtn!.trigger('click')

    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
