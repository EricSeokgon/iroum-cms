// 드리프트 알림 화면 — Vitest 단위 테스트 (SPEC-CMS-AI-001 Step 3)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import DriftAlerts from '@/views/ai/DriftAlerts.vue'
import { aiAdminApi } from '@/api/aiAdminApi'
import type { AiDriftAlertDto } from '@/types/ai'

vi.mock('@/api/aiAdminApi', () => ({
  aiAdminApi: {
    getMetrics: vi.fn(),
    getModelHealth: vi.fn(),
    getDriftAlerts: vi.fn(),
    getRetrainQueue: vi.fn(),
    requestRetrain: vi.fn(),
    updateRetrainStatus: vi.fn(),
    getSimulationStats: vi.fn(),
    triggerAggregate: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function makeAlert(overrides: Partial<AiDriftAlertDto> = {}): AiDriftAlertDto {
  return {
    id: 1,
    modelName: 'growth-stage-v1',
    predictionType: 'GROWTH_STAGE',
    driftDetected: true,
    accuracy: 0.72,
    rmse: 0.31,
    periodStart: '2026-05-10',
    createdAt: '2026-05-10T00:00:00Z',
    ...overrides,
  }
}

function mountView() {
  return mount(DriftAlerts, {
    global: { plugins: [i18n, createTestingPinia()] },
  })
}

describe('DriftAlerts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('드리프트 알림 목록을 날짜 내림차순으로 렌더링한다', async () => {
    vi.mocked(aiAdminApi.getDriftAlerts).mockResolvedValueOnce({
      data: [
        makeAlert({ id: 1, modelName: 'old-model', createdAt: '2026-05-01T00:00:00Z' }),
        makeAlert({ id: 2, modelName: 'new-model', createdAt: '2026-05-15T00:00:00Z' }),
      ],
    } as never)

    const wrapper = mountView()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('new-model')
    expect(text).toContain('old-model')
    // 최신(new-model)이 먼저 노출
    expect(text.indexOf('new-model')).toBeLessThan(text.indexOf('old-model'))
  })

  it('"재학습 요청" 버튼이 POST retrain-queue 를 호출한다', async () => {
    vi.mocked(aiAdminApi.getDriftAlerts).mockResolvedValueOnce({
      data: [makeAlert({ id: 1, modelName: 'growth-stage-v1' })],
    } as never)
    vi.mocked(aiAdminApi.requestRetrain).mockResolvedValueOnce({
      data: { id: 99, modelName: 'growth-stage-v1', status: 'QUEUED' },
    } as never)

    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as {
      onRequestRetrain: (a: AiDriftAlertDto) => void
      confirmRetrain: () => Promise<void>
    }
    vm.onRequestRetrain(makeAlert({ modelName: 'growth-stage-v1' }))
    await vm.confirmRetrain()
    await flushPromises()

    expect(aiAdminApi.requestRetrain).toHaveBeenCalledWith(
      expect.objectContaining({ modelName: 'growth-stage-v1', triggerReason: 'MANUAL' }),
    )
  })

  it('알림이 없으면 빈 상태 메시지를 보여준다', async () => {
    vi.mocked(aiAdminApi.getDriftAlerts).mockResolvedValueOnce({ data: [] } as never)

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('현재 감지된 드리프트가 없습니다')
  })
})
