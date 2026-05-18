// 재학습 큐 화면 — Vitest 단위 테스트 (SPEC-CMS-AI-001 Step 3)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import RetrainQueue from '@/views/ai/RetrainQueue.vue'
import { aiAdminApi } from '@/api/aiAdminApi'
import type { RetrainStatusDto } from '@/types/ai'

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

function makeItem(overrides: Partial<RetrainStatusDto> = {}): RetrainStatusDto {
  return {
    id: 1,
    modelName: 'growth-stage-v1',
    triggerReason: 'DRIFT_ACCURACY',
    triggerDetail: {},
    status: 'QUEUED',
    requestedAt: '2026-05-10T00:00:00Z',
    updatedAt: '2026-05-10T00:00:00Z',
    ...overrides,
  }
}

function mountView() {
  return mount(RetrainQueue, {
    global: { plugins: [i18n, createTestingPinia()] },
  })
}

describe('RetrainQueue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('큐 항목을 상태와 함께 렌더링한다', async () => {
    vi.mocked(aiAdminApi.getRetrainQueue).mockResolvedValueOnce({
      data: [
        makeItem({ id: 1, status: 'QUEUED', modelName: 'growth-stage-v1' }),
        makeItem({ id: 2, status: 'DONE', modelName: 'risk-score-v2' }),
      ],
    } as never)

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('growth-stage-v1')
    expect(wrapper.text()).toContain('risk-score-v2')
  })

  it('상태별 뱃지 타입을 올바르게 매핑한다', async () => {
    vi.mocked(aiAdminApi.getRetrainQueue).mockResolvedValue({ data: [] } as never)

    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as { statusTagType: (s: string) => string }
    expect(vm.statusTagType('QUEUED')).toBe('info')
    expect(vm.statusTagType('IN_PROGRESS')).toBe('warning')
    expect(vm.statusTagType('DONE')).toBe('success')
    expect(vm.statusTagType('CANCELED')).toBe('danger')
    expect(vm.statusTagType('ACKNOWLEDGED')).toBe('primary')
  })

  it('승인 버튼이 PUT 으로 상태를 ACKNOWLEDGED 로 변경한다', async () => {
    vi.mocked(aiAdminApi.getRetrainQueue).mockResolvedValue({
      data: [makeItem({ id: 7, status: 'QUEUED' })],
    } as never)
    vi.mocked(aiAdminApi.updateRetrainStatus).mockResolvedValueOnce({
      data: makeItem({ id: 7, status: 'ACKNOWLEDGED' }),
    } as never)

    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as {
      onUpdateStatus: (id: number, status: string) => Promise<void>
    }
    await vm.onUpdateStatus(7, 'ACKNOWLEDGED')
    await flushPromises()

    expect(aiAdminApi.updateRetrainStatus).toHaveBeenCalledWith(7, { status: 'ACKNOWLEDGED' })
  })

  it('수동 재학습 폼 제출 시 requestRetrain 을 호출한다', async () => {
    vi.mocked(aiAdminApi.getRetrainQueue).mockResolvedValue({ data: [] } as never)
    vi.mocked(aiAdminApi.requestRetrain).mockResolvedValueOnce({
      data: makeItem({ id: 50, status: 'QUEUED' }),
    } as never)

    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as {
      manualModelName: string
      onManualRetrain: () => Promise<void>
    }
    vm.manualModelName = 'manual-model'
    await vm.onManualRetrain()
    await flushPromises()

    expect(aiAdminApi.requestRetrain).toHaveBeenCalledWith(
      expect.objectContaining({ modelName: 'manual-model', triggerReason: 'MANUAL' }),
    )
  })
})
