// SPEC-CMS-PUBLIC-001 T-009 — MediaCard 검증 (D-06 lazy load)
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

import MediaCard from '@/components/media/MediaCard.vue'
import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { MediaAssetSummary } from '@iroum/shared/types/api'

function makeI18n() {
  return createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
}

function makeImage(): MediaAssetSummary {
  return {
    uuid: 'img-1',
    fileName: 'photo.jpg',
    mediaType: 'IMAGE',
    mimeType: 'image/jpeg',
    sizeBytes: 102400,
    thumbnailUrl: 'https://cdn.example.com/photo.jpg',
    altText: '풍경 사진',
    tags: [],
    status: 'AVAILABLE',
    usageCount: 0,
    uploadedAt: '2026-04-15T10:00:00Z',
    uploadedBy: 'admin',
  }
}

function makeVideo(): MediaAssetSummary {
  return {
    uuid: 'vid-1',
    fileName: 'tutorial.mp4',
    mediaType: 'VIDEO',
    mimeType: 'video/mp4',
    sizeBytes: 5242880,
    thumbnailUrl: 'https://cdn.example.com/vid-thumb.jpg',
    altText: '튜토리얼 영상',
    tags: [],
    status: 'AVAILABLE',
    usageCount: 0,
    uploadedAt: '2026-04-15T10:00:00Z',
    uploadedBy: 'admin',
  }
}

function makeDocument(): MediaAssetSummary {
  return {
    uuid: 'doc-1',
    fileName: 'report.pdf',
    mediaType: 'DOCUMENT',
    mimeType: 'application/pdf',
    sizeBytes: 204800,
    thumbnailUrl: null,
    altText: null,
    tags: [],
    status: 'AVAILABLE',
    usageCount: 0,
    uploadedAt: '2026-04-15T10:00:00Z',
    uploadedBy: 'admin',
  }
}

describe('MediaCard — D-06 이미지 lazy load', () => {
  it('IMAGE 타입은 loading="lazy" decoding="async" img 를 렌더링한다', () => {
    const wrapper = mount(MediaCard, {
      props: { item: makeImage() },
      global: { plugins: [makeI18n()] },
    })
    const img = wrapper.find('img[data-testid="media-image"]')
    expect(img.exists()).toBe(true)
    expect(img.attributes('loading')).toBe('lazy')
    expect(img.attributes('decoding')).toBe('async')
    expect(img.attributes('alt')).toBe('풍경 사진')
    expect(img.attributes('src')).toBe('https://cdn.example.com/photo.jpg')
  })

  it('altText 가 없으면 fileName 을 alt 로 사용한다', () => {
    const item = makeImage()
    item.altText = null
    const wrapper = mount(MediaCard, {
      props: { item },
      global: { plugins: [makeI18n()] },
    })
    const img = wrapper.find('[data-testid="media-image"]')
    expect(img.attributes('alt')).toBe('photo.jpg')
  })
})

describe('MediaCard — D-06 비디오', () => {
  it('VIDEO 타입은 썸네일 + open 버튼을 렌더링한다', () => {
    const wrapper = mount(MediaCard, {
      props: { item: makeVideo() },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.find('[data-testid="media-video-open"]').exists()).toBe(true)
    const thumb = wrapper.find('[data-testid="media-video-thumbnail"]')
    expect(thumb.exists()).toBe(true)
    expect(thumb.attributes('loading')).toBe('lazy')
  })

  it('VIDEO 클릭 시 모달 영상이 표시된다', async () => {
    const wrapper = mount(MediaCard, {
      props: { item: makeVideo() },
      global: { plugins: [makeI18n()] },
      attachTo: document.body,
    })
    await wrapper.find('[data-testid="media-video-open"]').trigger('click')
    // el-dialog 는 teleport 로 body 에 렌더링됨 → document 검색
    await new Promise((r) => setTimeout(r, 50))
    const video = document.querySelector('video[data-testid="media-video-player"]')
    expect(video).toBeTruthy()
    wrapper.unmount()
  })
})

describe('MediaCard — DOCUMENT', () => {
  it('DOCUMENT 타입은 다운로드 링크를 렌더링한다', () => {
    const wrapper = mount(MediaCard, {
      props: { item: makeDocument() },
      global: { plugins: [makeI18n()] },
    })
    const link = wrapper.find('[data-testid="media-document-link"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('aria-label')).toContain('report.pdf')
  })
})
