// SPEC-CMS-AI-003 AC-RAG-010 — PolicyRagView 검증 (시민 RAG 질의응답)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const ragQueryMock = vi.fn()
const ragFeedbackMock = vi.fn()
vi.mock('@/api/policyApi', () => ({
  policyApi: {
    ragQuery: (...args: unknown[]) => ragQueryMock(...args),
    ragFeedback: (...args: unknown[]) => ragFeedbackMock(...args),
  },
}))

async function mountView(locale: 'ko' | 'en' = 'ko') {
  const i18n = createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/policies/:id', name: 'policy-detail', component: { template: '<div />' } },
      {
        path: '/policies/ask',
        name: 'policy-rag',
        component: () => import('@/views/ai/PolicyRagView.vue'),
      },
    ],
  })
  router.push('/policies/ask')
  await router.isReady()
  const View = (await import('@/views/ai/PolicyRagView.vue')).default
  const wrapper = mount(View, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('PolicyRagView — SPEC-CMS-AI-003 RAG 질의응답', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    ragQueryMock.mockReset()
    ragFeedbackMock.mockReset()
    ragFeedbackMock.mockResolvedValue(undefined)
  })

  it('질문 제출 → ragQuery 호출 + 답변/출처 렌더링 (i18n ko)', async () => {
    ragQueryMock.mockResolvedValue({
      answer: '청년 창업 지원 정책 안내입니다.',
      sources: [
        { id: 101, title: '청년 창업 자금', relevance: 0.91 },
        { id: 102, title: '소상공인 융자', relevance: 0.72 },
      ],
      degraded: false,
      cached: false,
      queryRef: 'ref-1',
    })
    const { wrapper } = await mountView()

    await wrapper.find('[data-testid="rag-question-input"]')
      .setValue('청년 창업 지원 정책 알려줘')
    await wrapper.find('form[data-testid="rag-form"]').trigger('submit')
    await flushPromises()

    expect(ragQueryMock).toHaveBeenCalledWith({ question: '청년 창업 지원 정책 알려줘' })
    expect(wrapper.find('[data-testid="rag-answer"]').text())
      .toContain('청년 창업 지원 정책 안내입니다.')
    expect(wrapper.find('[data-testid="rag-source-101"]').text()).toContain('청년 창업 자금')
    expect(wrapper.find('[data-testid="rag-sources"]').exists()).toBe(true)
  })

  it('AC-RAG-010: degraded=true → 간소 검색 결과 안내 배너 표시', async () => {
    ragQueryMock.mockResolvedValue({
      answer: '[간소 검색 결과] ...',
      sources: [{ id: 1, title: '폴백 정책', relevance: 0.5 }],
      degraded: true,
      cached: false,
      queryRef: 'ref-2',
    })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="rag-question-input"]').setValue('창업 지원')
    await wrapper.find('form[data-testid="rag-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="rag-degraded-banner"]').exists()).toBe(true)
  })

  it('AC-RAG-010: HELPFUL 피드백 버튼 클릭 → ragFeedback 호출 + 감사 메시지', async () => {
    ragQueryMock.mockResolvedValue({
      answer: 'A', sources: [{ id: 5, title: 'P', relevance: 0.8 }],
      degraded: false, cached: false, queryRef: 'ref-3',
    })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="rag-question-input"]').setValue('질문')
    await wrapper.find('form[data-testid="rag-form"]').trigger('submit')
    await flushPromises()

    await wrapper.find('[data-testid="rag-helpful"]').trigger('click')
    await flushPromises()

    expect(ragFeedbackMock).toHaveBeenCalledWith({ queryRef: 'ref-3', feedback: 'HELPFUL' })
    expect(wrapper.find('[data-testid="rag-feedback-thanks"]').exists()).toBe(true)
  })

  it('빈 질문 제출 → 클라이언트 검증 에러, ragQuery 미호출', async () => {
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="rag-question-input"]').setValue('   ')
    await wrapper.find('form[data-testid="rag-form"]').trigger('submit')
    await flushPromises()

    expect(ragQueryMock).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="rag-input-error"]').exists()).toBe(true)
  })

  it('AC-RAG-010: 영문(en) i18n 렌더링', async () => {
    const { wrapper } = await mountView('en')
    expect(wrapper.text()).toContain('Policy AI Q&A')
    expect(wrapper.find('[data-testid="rag-submit"]').text()).toBe('Ask')
  })

  it('빈 출처 → empty 메시지', async () => {
    ragQueryMock.mockResolvedValue({
      answer: '관련 정책을 찾지 못했습니다.',
      sources: [], degraded: false, cached: false, queryRef: 'ref-4',
    })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="rag-question-input"]').setValue('존재하지않는질문')
    await wrapper.find('form[data-testid="rag-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('관련 정책을 찾지 못했습니다')
  })

  it('policy-rag 라우트는 requiresAuth 메타가 false 또는 미설정 (공개)', async () => {
    const router = (await import('@/router')).default
    const route = router.getRoutes().find((r) => r.name === 'policy-rag')
    expect(route).toBeDefined()
    expect(route?.meta.requiresAuth).not.toBe(true)
  })
})
