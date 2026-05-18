// AI 모델 대시보드 화면 — Vitest 단위 테스트 (SPEC-CMS-AI-001 Step 3)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import ModelDashboard from '@/views/ai/ModelDashboard.vue'
import { aiAdminApi } from '@/api/aiAdminApi'
import type { AiMetricDto } from '@/types/ai'

// aiAdminApi mock
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

function makeMetric(overrides: Partial<AiMetricDto> = {}): AiMetricDto {
  return {
    id: 1,
    modelName: 'growth-stage-v1',
    predictionType: 'GROWTH_STAGE',
    aggregatePeriod: 'DAILY',
    periodStart: '2026-05-01',
    rmse: 0.12,
    mae: 0.08,
    accuracy: 0.91,
    latencyP50: 40,
    latencyP95: 120,
    latencyP99: 200,
    sampleCount: 500,
    driftDetected: false,
    createdAt: '2026-05-01T00:00:00Z',
    ...overrides,
  }
}

function mountView() {
  return mount(ModelDashboard, {
    global: { plugins: [i18n, createTestingPinia()] },
  })
}

describe('ModelDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(aiAdminApi.getModelHealth).mockResolvedValue({
      data: { status: 'UP', loadedModels: ['growth-stage-v1', 'risk-score-v2'] },
    } as never)
  })

  it('데이터 로드 시 메트릭 테이블을 렌더링한다', async () => {
    vi.mocked(aiAdminApi.getMetrics).mockResolvedValueOnce({
      data: [makeMetric({ modelName: 'growth-stage-v1', accuracy: 0.91 })],
    } as never)

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('growth-stage-v1')
    expect(wrapper.text()).toContain('91')
  })

  it('driftDetected=true 인 메트릭이 있으면 드리프트 알림 뱃지를 보여준다', async () => {
    vi.mocked(aiAdminApi.getMetrics).mockResolvedValueOnce({
      data: [
        makeMetric({ id: 1, driftDetected: false }),
        makeMetric({ id: 2, driftDetected: true }),
      ],
    } as never)

    const wrapper = mountView()
    await flushPromises()

    const badge = wrapper.find('[data-testid="drift-count-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('1')
  })

  it('modelName 으로 필터링하면 API 파라미터에 modelName 이 포함된다', async () => {
    vi.mocked(aiAdminApi.getMetrics).mockResolvedValue({ data: [] } as never)

    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as { filterModelName: string; onSearch: () => void }
    vm.filterModelName = 'risk-score-v2'
    vm.onSearch()
    await flushPromises()

    expect(aiAdminApi.getMetrics).toHaveBeenCalledWith(
      expect.objectContaining({ modelName: 'risk-score-v2' }),
    )
  })

  it('모델 헬스 상태를 표시한다', async () => {
    vi.mocked(aiAdminApi.getMetrics).mockResolvedValueOnce({ data: [] } as never)

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('UP')
    expect(wrapper.text()).toContain('risk-score-v2')
  })
})
