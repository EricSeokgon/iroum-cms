// 대시보드 스토어 단위 테스트 — SPEC-CMS-008
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    widgets: {
      list: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      data: vi.fn(),
      preview: vi.fn(),
    },
    layouts: {
      list: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      clone: vi.fn(),
      setDefault: vi.fn(),
    },
    views: {
      list: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      apply: vi.fn(),
    },
    exports: {
      create: vi.fn(),
      status: vi.fn(),
      history: vi.fn(),
    },
    cache: {
      invalidate: vi.fn(),
      stats: vi.fn(),
    },
  },
}))

import { useDashboardStore } from '@/stores/dashboardStore'
import { dashboardApi } from '@/api/dashboard'

const WIDGET = { id: 1, name: '매출 차트', widgetType: 'BAR', dataSourceType: 'QUERY' } as any
const LAYOUT = { id: 10, name: '기본 레이아웃', isDefault: true } as any
const VIEW = { id: 20, name: '분기별 뷰', dashboardId: 10 } as any
const EXPORT = { id: 30, status: 'PENDING', format: 'CSV' } as any
const CACHE_STATS = { hitCount: 100, missCount: 10 } as any

describe('useDashboardStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  // ── 위젯 ─────────────────────────────────────────────────────────────────
  describe('fetchWidgets', () => {
    it('성공 시 widgets 상태를 채운다', async () => {
      vi.mocked(dashboardApi.widgets.list).mockResolvedValueOnce({ data: [WIDGET] })

      const store = useDashboardStore()
      await store.fetchWidgets()

      expect(store.widgets).toEqual([WIDGET])
      expect(store.widgetLoading).toBe(false)
      expect(store.error).toBeNull()
    })

    it('실패 시 error를 설정하고 widgetLoading을 false로 복원한다', async () => {
      vi.mocked(dashboardApi.widgets.list).mockRejectedValueOnce(new Error('network'))

      const store = useDashboardStore()
      await store.fetchWidgets()

      expect(store.error).toBe('network')
      expect(store.widgetLoading).toBe(false)
    })

    it('실패 시 Error 인스턴스가 아니면 fallback 메시지를 사용한다', async () => {
      vi.mocked(dashboardApi.widgets.list).mockRejectedValueOnce('unknown')

      const store = useDashboardStore()
      await store.fetchWidgets()

      expect(store.error).toBe('위젯 목록 조회 실패')
    })
  })

  describe('createWidget', () => {
    it('생성된 위젯을 반환한다', async () => {
      vi.mocked(dashboardApi.widgets.create).mockResolvedValueOnce({ data: WIDGET })

      const store = useDashboardStore()
      const result = await store.createWidget({ name: '매출 차트' } as any)

      expect(result).toEqual(WIDGET)
    })
  })

  describe('updateWidget', () => {
    it('수정된 위젯을 반환한다', async () => {
      const updated = { ...WIDGET, name: '수정된 차트' }
      vi.mocked(dashboardApi.widgets.update).mockResolvedValueOnce({ data: updated })

      const store = useDashboardStore()
      const result = await store.updateWidget(1, { name: '수정된 차트' } as any)

      expect(result.name).toBe('수정된 차트')
    })
  })

  describe('deleteWidget', () => {
    it('API를 호출한다', async () => {
      vi.mocked(dashboardApi.widgets.delete).mockResolvedValueOnce(undefined)

      const store = useDashboardStore()
      await store.deleteWidget(1)

      expect(dashboardApi.widgets.delete).toHaveBeenCalledWith(1)
    })
  })

  describe('fetchWidgetData', () => {
    it('데이터를 widgetDataMap에 캐싱한다', async () => {
      const DATA = { rows: [] } as any
      vi.mocked(dashboardApi.widgets.data).mockResolvedValueOnce({ data: DATA })

      const store = useDashboardStore()
      const result = await store.fetchWidgetData(1)

      expect(result).toEqual(DATA)
      expect(store.widgetDataMap[1]).toEqual(DATA)
      expect(store.widgetDataLoading[1]).toBe(false)
    })
  })

  describe('previewWidget', () => {
    it('미리보기 데이터를 반환한다', async () => {
      const DATA = { rows: [] } as any
      vi.mocked(dashboardApi.widgets.preview).mockResolvedValueOnce({ data: DATA })

      const store = useDashboardStore()
      const result = await store.previewWidget({ name: 'test' } as any, ['ADMIN'])

      expect(result).toEqual(DATA)
    })
  })

  // ── 레이아웃 ──────────────────────────────────────────────────────────────
  describe('fetchLayouts', () => {
    it('성공 시 layouts 상태를 채운다', async () => {
      vi.mocked(dashboardApi.layouts.list).mockResolvedValueOnce({ data: [LAYOUT] })

      const store = useDashboardStore()
      await store.fetchLayouts()

      expect(store.layouts).toEqual([LAYOUT])
      expect(store.layoutLoading).toBe(false)
    })

    it('실패 시 error를 설정한다', async () => {
      vi.mocked(dashboardApi.layouts.list).mockRejectedValueOnce(new Error('timeout'))

      const store = useDashboardStore()
      await store.fetchLayouts()

      expect(store.error).toBe('timeout')
    })
  })

  describe('fetchLayout', () => {
    it('단건 조회 시 currentLayout을 설정한다', async () => {
      vi.mocked(dashboardApi.layouts.get).mockResolvedValueOnce({ data: LAYOUT })

      const store = useDashboardStore()
      await store.fetchLayout(10)

      expect(store.currentLayout).toEqual(LAYOUT)
    })

    it('실패 시 fallback error 메시지를 설정한다', async () => {
      vi.mocked(dashboardApi.layouts.get).mockRejectedValueOnce('not object')

      const store = useDashboardStore()
      await store.fetchLayout(10)

      expect(store.error).toBe('레이아웃 상세 조회 실패')
    })
  })

  describe('createLayout / updateLayout / deleteLayout / cloneLayout / setDefaultLayout', () => {
    it('createLayout은 생성된 레이아웃을 반환한다', async () => {
      vi.mocked(dashboardApi.layouts.create).mockResolvedValueOnce({ data: LAYOUT })

      const store = useDashboardStore()
      const result = await store.createLayout({ name: '기본' } as any)

      expect(result).toEqual(LAYOUT)
    })

    it('cloneLayout은 복제된 레이아웃을 반환한다', async () => {
      const cloned = { ...LAYOUT, id: 11 }
      vi.mocked(dashboardApi.layouts.clone).mockResolvedValueOnce({ data: cloned })

      const store = useDashboardStore()
      const result = await store.cloneLayout(10)

      expect(result.id).toBe(11)
    })

    it('setDefaultLayout은 기본 레이아웃을 반환한다', async () => {
      vi.mocked(dashboardApi.layouts.setDefault).mockResolvedValueOnce({ data: LAYOUT })

      const store = useDashboardStore()
      const result = await store.setDefaultLayout(10)

      expect(result.isDefault).toBe(true)
    })

    it('deleteLayout은 API를 호출한다', async () => {
      vi.mocked(dashboardApi.layouts.delete).mockResolvedValueOnce(undefined)

      const store = useDashboardStore()
      await store.deleteLayout(10)

      expect(dashboardApi.layouts.delete).toHaveBeenCalledWith(10)
    })
  })

  // ── 저장된 뷰 ────────────────────────────────────────────────────────────
  describe('fetchViews', () => {
    it('성공 시 views 상태를 채운다', async () => {
      vi.mocked(dashboardApi.views.list).mockResolvedValueOnce({ data: [VIEW] })

      const store = useDashboardStore()
      await store.fetchViews(10)

      expect(store.views).toEqual([VIEW])
      expect(store.viewLoading).toBe(false)
    })

    it('실패 시 error를 설정한다', async () => {
      vi.mocked(dashboardApi.views.list).mockRejectedValueOnce(new Error('403'))

      const store = useDashboardStore()
      await store.fetchViews()

      expect(store.error).toBe('403')
    })
  })

  describe('saveView', () => {
    it('저장 후 views 배열에 추가한다', async () => {
      vi.mocked(dashboardApi.views.create).mockResolvedValueOnce({ data: VIEW })

      const store = useDashboardStore()
      store.views = []
      const result = await store.saveView({ name: '분기별 뷰' } as any)

      expect(result).toEqual(VIEW)
      expect(store.views).toContainEqual(VIEW)
    })
  })

  describe('updateView', () => {
    it('기존 뷰를 인덱스 위치에서 교체한다', async () => {
      const updated = { ...VIEW, name: '수정된 뷰' }
      vi.mocked(dashboardApi.views.update).mockResolvedValueOnce({ data: updated })

      const store = useDashboardStore()
      store.views = [VIEW]
      await store.updateView(20, { name: '수정된 뷰' } as any)

      expect(store.views[0].name).toBe('수정된 뷰')
    })

    it('일치하는 id가 없으면 배열을 변경하지 않는다', async () => {
      const updated = { ...VIEW, id: 99 }
      vi.mocked(dashboardApi.views.update).mockResolvedValueOnce({ data: updated })

      const store = useDashboardStore()
      store.views = [VIEW]
      await store.updateView(99, {} as any)

      expect(store.views[0]).toEqual(VIEW)
    })
  })

  describe('deleteView', () => {
    it('삭제 후 views 배열에서 제거한다', async () => {
      vi.mocked(dashboardApi.views.delete).mockResolvedValueOnce(undefined)

      const store = useDashboardStore()
      store.views = [VIEW, { ...VIEW, id: 21 }]
      await store.deleteView(20)

      expect(store.views).toHaveLength(1)
      expect(store.views[0].id).toBe(21)
    })
  })

  describe('applyView', () => {
    it('적용된 뷰를 반환한다', async () => {
      vi.mocked(dashboardApi.views.apply).mockResolvedValueOnce({ data: VIEW })

      const store = useDashboardStore()
      const result = await store.applyView(20)

      expect(result).toEqual(VIEW)
    })
  })

  // ── 내보내기 ──────────────────────────────────────────────────────────────
  describe('requestExport', () => {
    it('내보내기 요청 후 ExportResponse를 반환한다', async () => {
      vi.mocked(dashboardApi.exports.create).mockResolvedValueOnce({ data: EXPORT })

      const store = useDashboardStore()
      const result = await store.requestExport({ format: 'CSV' } as any)

      expect(result).toEqual(EXPORT)
    })
  })

  describe('pollExportStatus', () => {
    it('상태 조회 후 exportHistory를 갱신한다', async () => {
      const updated = { ...EXPORT, status: 'DONE' }
      vi.mocked(dashboardApi.exports.status).mockResolvedValueOnce({ data: updated })

      const store = useDashboardStore()
      store.exportHistory = [EXPORT]
      const result = await store.pollExportStatus(30)

      expect(result.status).toBe('DONE')
      expect(store.exportHistory[0].status).toBe('DONE')
    })

    it('exportHistory에 없는 id면 배열을 변경하지 않는다', async () => {
      const updated = { ...EXPORT, id: 99, status: 'DONE' }
      vi.mocked(dashboardApi.exports.status).mockResolvedValueOnce({ data: updated })

      const store = useDashboardStore()
      store.exportHistory = [EXPORT]
      await store.pollExportStatus(99)

      expect(store.exportHistory[0].status).toBe('PENDING')
    })
  })

  describe('listExportHistory', () => {
    it('성공 시 exportHistory를 채운다', async () => {
      vi.mocked(dashboardApi.exports.history).mockResolvedValueOnce({ data: [EXPORT] })

      const store = useDashboardStore()
      await store.listExportHistory()

      expect(store.exportHistory).toEqual([EXPORT])
      expect(store.exportLoading).toBe(false)
    })

    it('실패 시 error를 설정하고 exportLoading을 복원한다', async () => {
      vi.mocked(dashboardApi.exports.history).mockRejectedValueOnce(new Error('500'))

      const store = useDashboardStore()
      await store.listExportHistory()

      expect(store.error).toBe('500')
      expect(store.exportLoading).toBe(false)
    })
  })

  // ── 캐시 ─────────────────────────────────────────────────────────────────
  describe('invalidateCache', () => {
    it('API 호출 후 widgetDataMap을 비운다', async () => {
      vi.mocked(dashboardApi.cache.invalidate).mockResolvedValueOnce(undefined)

      const store = useDashboardStore()
      store.widgetDataMap = { 1: { rows: [] } as any }
      await store.invalidateCache({ widgetIds: [1] } as any)

      expect(store.widgetDataMap).toEqual({})
      expect(dashboardApi.cache.invalidate).toHaveBeenCalledWith({ widgetIds: [1] })
    })
  })

  describe('fetchCacheStats', () => {
    it('성공 시 cacheStats를 설정한다', async () => {
      vi.mocked(dashboardApi.cache.stats).mockResolvedValueOnce({ data: CACHE_STATS })

      const store = useDashboardStore()
      await store.fetchCacheStats()

      expect(store.cacheStats).toEqual(CACHE_STATS)
    })

    it('실패 시 error를 설정한다', async () => {
      vi.mocked(dashboardApi.cache.stats).mockRejectedValueOnce(new Error('503'))

      const store = useDashboardStore()
      await store.fetchCacheStats()

      expect(store.error).toBe('503')
    })
  })

  // ── 공통 상태 초기값 ───────────────────────────────────────────────────────
  describe('초기 상태', () => {
    it('모든 목록 상태가 빈 배열/null이다', () => {
      const store = useDashboardStore()

      expect(store.widgets).toEqual([])
      expect(store.layouts).toEqual([])
      expect(store.views).toEqual([])
      expect(store.exportHistory).toEqual([])
      expect(store.currentLayout).toBeNull()
      expect(store.cacheStats).toBeNull()
      expect(store.error).toBeNull()
    })

    it('모든 로딩 플래그가 false이다', () => {
      const store = useDashboardStore()

      expect(store.widgetLoading).toBe(false)
      expect(store.layoutLoading).toBe(false)
      expect(store.viewLoading).toBe(false)
      expect(store.exportLoading).toBe(false)
    })
  })
})
