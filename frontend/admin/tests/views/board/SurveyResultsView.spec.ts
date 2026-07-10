/**
 * SurveyResultsView 단위 테스트 — SPEC-CMS-SURVEY-001 (AC-001, AC-004, AC-005, AC-023)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import SurveyResultsView from '../../../src/views/board/SurveyResultsView.vue'
import type { SurveyResultDto } from '../../../src/api/survey'

vi.mock('../../../src/api/survey', () => ({
  getSurveyResults: vi.fn(),
  exportSurveyResults: vi.fn(),
}))

const hasPermissionMock = vi.fn<(code: string) => boolean>(() => false)
vi.mock('../../../src/stores/permissionStore', () => ({
  usePermissionStore: () => ({ hasPermission: hasPermissionMock }),
}))

const routerPush = vi.fn()
const routerBack = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush, back: routerBack }),
}))

import { getSurveyResults, exportSurveyResults } from '../../../src/api/survey'

const mockGetResults = vi.mocked(getSurveyResults)
const mockExport = vi.mocked(exportSurveyResults)

const RESULT: SurveyResultDto = {
  surveyId: 1,
  title: '만족도 조사',
  totalResponses: 10,
  questions: [
    {
      questionId: 11,
      questionText: '만족하십니까?',
      questionType: 'SINGLE',
      totalAnswers: 10,
      distribution: [
        { label: '매우 만족', count: 7, percentage: 70 },
        { label: '보통', count: 3, percentage: 30 },
      ],
    },
  ],
}

function buildWrapper() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackWarn: false,
    missingWarn: false,
    messages: { ko: {} },
  })
  return mount(SurveyResultsView, {
    props: { id: '1' },
    global: { plugins: [pinia, i18n, ElementPlus] },
  })
}

describe('SurveyResultsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hasPermissionMock.mockReturnValue(false)
    mockGetResults.mockResolvedValue({ data: RESULT })
  })

  it('AC-001/002: 결과를 로드하고 질문별 분포를 렌더링한다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()

    expect(mockGetResults).toHaveBeenCalledWith(1)
    expect(wrapper.findAll('[data-testid="question-result"]').length).toBe(1)
  })

  it('AC-005: SURVEY:EXPORT 권한이 없으면 내보내기 버튼이 숨겨진다', async () => {
    hasPermissionMock.mockReturnValue(false)
    const wrapper = buildWrapper()
    await flushPromises()

    expect(wrapper.find('[data-testid="export-csv-btn"]').exists()).toBe(false)
  })

  it('AC-005: SURVEY:EXPORT 권한이 있으면 내보내기 버튼이 노출되고 클릭 시 Blob 다운로드를 트리거한다', async () => {
    hasPermissionMock.mockReturnValue(true)
    mockExport.mockResolvedValue(new Blob(['﻿a,b'], { type: 'text/csv' }))

    const createUrl = vi.fn(() => 'blob:mock')
    const revokeUrl = vi.fn()
    // @ts-expect-error jsdom 환경 stub
    window.URL.createObjectURL = createUrl
    // @ts-expect-error jsdom 환경 stub
    window.URL.revokeObjectURL = revokeUrl

    const wrapper = buildWrapper()
    await flushPromises()

    const btn = wrapper.find('[data-testid="export-csv-btn"]')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()

    expect(mockExport).toHaveBeenCalledWith(1)
    expect(createUrl).toHaveBeenCalled()
  })

  it('AC-023: 응답이 없으면 빈 상태 메시지를 렌더링한다', async () => {
    mockGetResults.mockResolvedValue({ data: { ...RESULT, totalResponses: 0, questions: [] } })
    const wrapper = buildWrapper()
    await flushPromises()

    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
  })
})
