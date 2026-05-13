// ExportHistoryView 단위 테스트 — SPEC-CMS-008 REQ-VIZ-006
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import ExportHistoryView from '@/views/dashboard/ExportHistoryView.vue'
import { dashboardApi } from '@/api/dashboard'
import type { ExportResponse } from '@/api/dashboard'

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    widgets: { list: vi.fn(), data: vi.fn() },
    views: { list: vi.fn() },
    exports: {
      create: vi.fn(),
      status: vi.fn(),
      history: vi.fn(),
      download: vi.fn((id: number, sig?: string) => {
        return sig
          ? `/api/v1/dashboard/export/${id}/download?sig=${sig}`
          : `/api/v1/dashboard/export/${id}/download`
      }),
    },
    layouts: { list: vi.fn() },
    cache: { invalidate: vi.fn(), stats: vi.fn() },
  },
}))

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: { ko: {} },
})

// 1년 후 만료 (유효)
const FUTURE_DATE = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString()
// 1일 전 만료 (만료됨)
const PAST_DATE = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()

const MOCK_HISTORY: ExportResponse[] = [
  {
    id: 1,
    requestor_id: 1,
    export_type: 'EXCEL',
    scope: '{"dashboard_id":5}',
    status: 'COMPLETED',
    row_count: 1500,
    requested_at: '2026-05-12T10:00:00Z',
    completed_at: '2026-05-12T10:01:00Z',
    expires_at: FUTURE_DATE,
    signed_download_url: 'https://cdn.example.com/exports/1.xlsx?sig=abc',
  },
  {
    id: 2,
    requestor_id: 1,
    export_type: 'CSV',
    scope: '{"widget_ids":[1,2]}',
    status: 'PROCESSING',
    progress_pct: 45,
    requested_at: '2026-05-13T09:00:00Z',
  },
  {
    id: 3,
    requestor_id: 1,
    export_type: 'PDF',
    scope: '{"dashboard_id":3}',
    status: 'FAILED',
    error_message: 'Out of memory',
    requested_at: '2026-05-12T08:00:00Z',
  },
  {
    id: 4,
    requestor_id: 1,
    export_type: 'EXCEL',
    scope: '{"dashboard_id":2}',
    status: 'COMPLETED',
    row_count: 200,
    requested_at: '2026-05-01T00:00:00Z',
    completed_at: '2026-05-01T00:01:00Z',
    expires_at: PAST_DATE,
  },
]

function buildWrapper() {
  return mount(ExportHistoryView, {
    global: {
      plugins: [
        i18n,
        ElementPlus,
        createTestingPinia({ stubActions: false }),
      ],
    },
  })
}

describe('ExportHistoryView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.clearAllMocks()
    vi.mocked(dashboardApi.exports.history).mockResolvedValue({
      data: MOCK_HISTORY,
    } as never)
    vi.mocked(dashboardApi.exports.status).mockResolvedValue({
      data: { ...MOCK_HISTORY[1], status: 'COMPLETED', progress_pct: 100 },
    } as never)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('마운트 시 내보내기 이력 API가 호출된다', async () => {
    buildWrapper()
    await flushPromises()
    expect(dashboardApi.exports.history).toHaveBeenCalled()
  })

  it('이력 행이 테이블에 렌더링된다 (EXCEL/CSV/PDF)', async () => {
    const wrapper = buildWrapper()
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('EXCEL')
    expect(html).toContain('CSV')
    expect(html).toContain('PDF')
    // 행 수 포맷팅 확인 (1500 → "1,500")
    expect(html).toContain('1,500')
  })

  it('이력이 비어있으면 empty 메시지를 표시한다', async () => {
    vi.mocked(dashboardApi.exports.history).mockResolvedValue({ data: [] } as never)
    const wrapper = buildWrapper()
    await flushPromises()
    expect(wrapper.html()).toContain('내보내기 이력이 없습니다')
  })

  it('상태 필터 적용 후 조회 시 status 파라미터로 API가 호출된다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()
    vi.mocked(dashboardApi.exports.history).mockClear()

    // 컴포넌트의 filter.status를 직접 조작
    const vm = wrapper.vm as unknown as {
      filter: { status?: string }
      refresh: () => Promise<void>
    }
    vm.filter.status = 'COMPLETED'
    await vm.refresh()
    await flushPromises()

    expect(dashboardApi.exports.history).toHaveBeenCalledWith('COMPLETED')
  })

  it('완료된 항목에 다운로드 버튼이 표시되고, 만료된 항목엔 표시되지 않는다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()
    const html = wrapper.html()
    // 만료되지 않은 COMPLETED 항목 (id=1) → 다운로드 버튼 노출
    expect(html).toContain('다운로드')
    // 만료된 항목 (id=4) → "만료됨" 태그 노출
    expect(html).toContain('만료됨')
  })

  it('PROCESSING 항목은 진행률(%)을 표시한다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()
    const html = wrapper.html()
    // PROCESSING 항목의 진행률 (45%)
    expect(html).toContain('45')
  })

  it('FAILED 항목은 "실패" 라벨이 표시된다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('실패')
  })

  it('PROCESSING 항목에 대해 폴링이 시작된다 (status API 주기 호출)', async () => {
    const wrapper = buildWrapper()
    await flushPromises()
    vi.mocked(dashboardApi.exports.status).mockClear()

    // 3초마다 폴링 — 3초 진행
    vi.advanceTimersByTime(3100)
    await flushPromises()

    expect(dashboardApi.exports.status).toHaveBeenCalledWith(2)
    void wrapper
  })
})
