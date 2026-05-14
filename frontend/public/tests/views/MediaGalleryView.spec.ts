// SPEC-CMS-PUBLIC-001 T-009 — MediaGalleryView 검증 (D-06 lazy load)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { MediaAssetSummary } from '@iroum/shared/types/api'

const listMock = vi.fn()
vi.mock('@/api/mediaApi', () => ({
  mediaApi: {
    list: (...args: unknown[]) => listMock(...args),
  },
}))

function makeImage(id: number): MediaAssetSummary {
  return {
    uuid: `img-${id}`,
    fileName: `photo-${id}.jpg`,
    mediaType: 'IMAGE',
    mimeType: 'image/jpeg',
    sizeBytes: 102400,
    thumbnailUrl: `https://cdn.example.com/photo-${id}.jpg`,
    altText: `사진 ${id}`,
    tags: [],
    status: 'AVAILABLE',
    usageCount: 0,
    uploadedAt: '2026-04-15T10:00:00Z',
    uploadedBy: 'admin',
  }
}

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/media', name: 'media-gallery', component: () => import('@/views/MediaGalleryView.vue') },
    ],
  })
  router.push('/media')
  await router.isReady()
  const MediaGalleryView = (await import('@/views/MediaGalleryView.vue')).default
  const wrapper = mount(MediaGalleryView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('MediaGalleryView — D-06 lazy load', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    localStorage.clear()
  })

  it('마운트 시 mediaApi.list({page:0, size:20}) 호출', async () => {
    listMock.mockResolvedValue({
      content: [makeImage(1)],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    await mountView()
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }))
  })

  it('4 개 필터 탭 (ALL/IMAGE/VIDEO/DOCUMENT) 가 렌더링된다', async () => {
    listMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="media-tab-ALL"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="media-tab-IMAGE"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="media-tab-VIDEO"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="media-tab-DOCUMENT"]').exists()).toBe(true)
  })

  it('IMAGE 탭 클릭 시 mediaApi.list({type:"IMAGE"}) 호출', async () => {
    listMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const wrapper = await mountView()
    listMock.mockClear()
    await wrapper.find('[data-testid="media-tab-IMAGE"]').trigger('click')
    await flushPromises()
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'IMAGE' }))
  })

  it('이미지 카드들이 loading="lazy" decoding="async" 속성을 가진다 (D-06)', async () => {
    listMock.mockResolvedValue({
      content: [makeImage(1), makeImage(2), makeImage(3)],
      page: 0,
      size: 20,
      totalElements: 3,
      totalPages: 1,
    })
    const wrapper = await mountView()
    const imgs = wrapper.findAll('img[data-testid="media-image"]')
    expect(imgs.length).toBe(3)
    imgs.forEach((img) => {
      expect(img.attributes('loading')).toBe('lazy')
      expect(img.attributes('decoding')).toBe('async')
    })
  })

  it('빈 결과 → EmptyState 표시', async () => {
    listMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
  })
})
