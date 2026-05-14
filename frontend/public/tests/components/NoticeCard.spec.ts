// SPEC-CMS-PUBLIC-001 T-006 — NoticeCard 컴포넌트 검증
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createMemoryHistory } from 'vue-router'

import NoticeCard from '@/components/notice/NoticeCard.vue'
import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { PostSummary } from '@iroum/shared/types/api'

async function makeHarness() {
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
      { path: '/notices/:id', name: 'notice-detail', component: { template: '<div />' } },
    ],
  })
  router.push('/notices')
  await router.isReady()
  return { i18n, router }
}

const sampleNotice: PostSummary = {
  id: 101,
  bbsId: 1,
  title: '2026 봄 세미나 안내',
  authorUsername: 'admin',
  viewCount: 42,
  likeCount: 0,
  status: 'PUBLISHED',
  isNotice: false,
  publishedAt: '2026-04-15T09:00:00Z',
  createdAt: '2026-04-15T09:00:00Z',
}

describe('NoticeCard', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('제목, 작성자, 조회수가 렌더링된다', async () => {
    const { i18n, router } = await makeHarness()
    const wrapper = mount(NoticeCard, {
      props: { notice: sampleNotice },
      global: { plugins: [i18n, router] },
    })
    expect(wrapper.text()).toContain('2026 봄 세미나 안내')
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('42')
    expect(wrapper.text()).toContain('2026-04-15')
  })

  it('isNotice=true 인 항목은 고정 뱃지를 표시한다', async () => {
    const { i18n, router } = await makeHarness()
    const wrapper = mount(NoticeCard, {
      props: { notice: { ...sampleNotice, isNotice: true } },
      global: { plugins: [i18n, router] },
    })
    expect(wrapper.find('[data-testid="pinned-badge"]').exists()).toBe(true)
  })

  it('isNotice=false 인 항목은 고정 뱃지를 표시하지 않는다', async () => {
    const { i18n, router } = await makeHarness()
    const wrapper = mount(NoticeCard, {
      props: { notice: sampleNotice },
      global: { plugins: [i18n, router] },
    })
    expect(wrapper.find('[data-testid="pinned-badge"]').exists()).toBe(false)
  })

  it('상세 라우트로 router-link 가 생성된다', async () => {
    const { i18n, router } = await makeHarness()
    const wrapper = mount(NoticeCard, {
      props: { notice: sampleNotice },
      global: { plugins: [i18n, router] },
    })
    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toContain('/notices/101')
  })
})
