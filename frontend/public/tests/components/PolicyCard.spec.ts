// SPEC-CMS-PUBLIC-001 T-007 — PolicyCard 컴포넌트 검증
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import PolicyCard from '@/components/policy/PolicyCard.vue'
import type { PolicySummary } from '@/api/policyApi'

function makePolicy(over: Partial<PolicySummary> = {}): PolicySummary {
  return {
    id: 1,
    title: '스타트업 자금 지원',
    industry: 'IT',
    region: '서울',
    type: '자금지원',
    supportAmount: '최대 1억원',
    deadline: '2026-08-31T00:00:00Z',
    ...over,
  }
}

function makeFixtures() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/policies', name: 'policy-list', component: { template: '<div />' } },
      { path: '/policies/:id', name: 'policy-detail', component: { template: '<div />' } },
    ],
  })
  return { i18n, router }
}

describe('PolicyCard — 기본 렌더링', () => {
  it('타입 뱃지 + 제목 + 메타(업종/지역) 표시', () => {
    const { i18n, router } = makeFixtures()
    const wrapper = mount(PolicyCard, {
      props: { policy: makePolicy() },
      global: { plugins: [i18n, router] },
    })
    const card = wrapper.find('[data-testid="policy-card"]')
    expect(card.exists()).toBe(true)
    expect(card.text()).toContain('스타트업 자금 지원')
    expect(card.text()).toContain('자금지원')
    expect(card.text()).toContain('IT')
    expect(card.text()).toContain('서울')
  })

  it('상세 라우터 링크가 policy-detail/:id 로 설정된다', () => {
    const { i18n, router } = makeFixtures()
    const wrapper = mount(PolicyCard, {
      props: { policy: makePolicy({ id: 42 }) },
      global: { plugins: [i18n, router] },
    })
    const link = wrapper.find('a')
    expect(link.attributes('href')).toBe('/policies/42')
  })

  it('matchScore 가 있으면 점수 뱃지 + reason 렌더링', () => {
    const { i18n, router } = makeFixtures()
    const wrapper = mount(PolicyCard, {
      props: {
        policy: makePolicy(),
        matchScore: 0.87,
        matchReason: '업종 일치',
      },
      global: { plugins: [i18n, router] },
    })
    expect(wrapper.find('[data-testid="policy-match-score"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('87%')
    expect(wrapper.find('[data-testid="policy-match-reason"]').text()).toContain('업종 일치')
  })
})
