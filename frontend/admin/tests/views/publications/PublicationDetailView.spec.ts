// PublicationDetailView 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/publication', () => ({
  listPublications: vi.fn(),
  getPublication: vi.fn().mockResolvedValue({ data: null }),
  getCategories: vi.fn().mockResolvedValue({ data: [] }),
  createPublication: vi.fn(),
  updatePublication: vi.fn(),
  deletePublication: vi.fn(),
  requestZipDownload: vi.fn(),
}))

import PublicationDetailView from '@/views/board/PublicationDetailView.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      publication: {
        title: '간행물',
        field: { year: '발행년', month: '발행월' },
      },
      common: { back: '뒤로' },
    },
  },
})

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/pub/:id', name: 'publication-detail', component: PublicationDetailView },
  ],
})

async function mountView() {
  await router.push({ name: 'publication-detail', params: { id: '1' } })
  await router.isReady()
  return mount(PublicationDetailView, {
    props: { id: '1' },
    global: { plugins: [i18n, router, createTestingPinia({ createSpy: vi.fn })] },
  })
}

describe('PublicationDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('뒤로 버튼을 노출한다', async () => {
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('뒤로')
  })

  it('로딩 컨테이너가 마운트된다', async () => {
    const wrapper = await mountView()
    expect(wrapper.find('div').exists()).toBe(true)
  })
})
