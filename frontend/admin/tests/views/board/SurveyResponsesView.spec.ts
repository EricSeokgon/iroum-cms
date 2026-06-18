/**
 * SurveyResponsesView 단위 테스트 — SPEC-CMS-SURVEY-001 (AC-006, AC-007, AC-008)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import SurveyResponsesView from '../../../src/views/board/SurveyResponsesView.vue'
import type { SurveyResponseItem } from '../../../src/api/survey'

vi.mock('../../../src/api/survey', () => ({
  getSurveyResponses: vi.fn(),
}))

const routerBack = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ back: routerBack }),
}))

import { getSurveyResponses } from '../../../src/api/survey'

const mockGetResponses = vi.mocked(getSurveyResponses)

const NAMED: SurveyResponseItem = {
  responseId: 7001,
  respondentId: 42,
  respondentName: '홍길동',
  submittedAt: '2026-06-18T00:00:00Z',
  answers: [{ questionId: 11, questionText: '질문1', questionType: 'TEXT', answerText: '응답' }],
}
const ANON: SurveyResponseItem = {
  responseId: 7002,
  respondentId: null,
  respondentName: null,
  submittedAt: '2026-06-18T01:00:00Z',
  answers: [],
}

function pageResp(items: SurveyResponseItem[], total: number) {
  return {
    data: { content: items, totalElements: total, totalPages: 1, page: 0, size: 20 },
  }
}

function buildWrapper() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackWarn: false,
    missingWarn: false,
    messages: {
      ko: {
        survey: { anonymous: '익명', viewDetail: '상세보기', responseDetail: '응답 상세', respondent: '응답자' },
        common: { actions: '작업', back: '뒤로' },
      },
    },
  })
  return mount(SurveyResponsesView, {
    props: { id: '1' },
    global: { plugins: [pinia, i18n, ElementPlus] },
  })
}

describe('SurveyResponsesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // @ts-expect-error 테스트 mock 반환 타입 단순화
    mockGetResponses.mockResolvedValue(pageResp([NAMED, ANON], 2))
  })

  it('AC-006: 응답 목록 테이블을 페이지네이션과 함께 렌더링한다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()

    expect(mockGetResponses).toHaveBeenCalledWith(1, 0, 20)
    expect(wrapper.find('[data-testid="responses-table"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('홍길동')
  })

  it('AC-007: 익명 응답은 "익명"으로 표기한다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()

    const cells = wrapper.findAll('[data-testid="respondent-cell"]')
    const texts = cells.map((c) => c.text())
    expect(texts).toContain('익명')
    expect(texts).toContain('홍길동')
  })

  it('AC-008: 상세보기 클릭 시 드로어로 질문별 답변을 표시한다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()

    const detailBtns = wrapper.findAll('.el-button').filter((b) => b.text().includes('상세보기'))
    expect(detailBtns.length).toBeGreaterThan(0)
    await detailBtns[0].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('질문1')
  })
})
