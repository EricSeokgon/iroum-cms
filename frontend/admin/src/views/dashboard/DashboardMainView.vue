<template>
  <!-- 대시보드 + KPI 시각화 — SPEC-CMS-008 REQ-VIZ-002 ~ 005 -->
  <div>
    <div class="mb-4 flex items-center justify-between flex-wrap gap-3">
      <h2 class="text-xl font-semibold text-gray-800">대시보드</h2>
      <div class="flex flex-wrap items-center gap-2">
        <!-- 저장된 뷰 -->
        <el-select
          v-model="selectedViewId"
          placeholder="저장된 뷰"
          clearable
          size="small"
          style="width: 200px"
          aria-label="저장된 뷰 선택"
          @change="onApplyView"
        >
          <el-option
            v-for="v in store.views"
            :key="v.id"
            :label="v.name"
            :value="v.id"
          />
        </el-select>
        <el-button :icon="Star" size="small" @click="openSaveView">뷰 저장</el-button>
        <!-- 내보내기 -->
        <el-button :icon="Download" size="small" @click="openExport">내보내기</el-button>
        <!-- 새로고침 -->
        <el-button :icon="Refresh" size="small" @click="loadAll">새로고침</el-button>
      </div>
    </div>

    <!-- 필터 바 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">기간</p>
          <el-select v-model="filter.period" size="small" style="width: 140px" aria-label="기간 선택">
            <el-option label="최근 7일" value="7d" />
            <el-option label="최근 30일" value="30d" />
            <el-option label="최근 90일" value="90d" />
            <el-option label="사용자 정의" value="custom" />
          </el-select>
        </div>
        <div v-if="filter.period === 'custom'">
          <p class="mb-1 text-xs text-gray-500">시작일</p>
          <el-date-picker v-model="filter.from" type="date" size="small" placeholder="시작일" value-format="YYYY-MM-DD" aria-label="시작일" />
        </div>
        <div v-if="filter.period === 'custom'">
          <p class="mb-1 text-xs text-gray-500">종료일</p>
          <el-date-picker v-model="filter.to" type="date" size="small" placeholder="종료일" value-format="YYYY-MM-DD" aria-label="종료일" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">기능 (Feature)</p>
          <el-select
            v-model="filter.features"
            multiple collapse-tags collapse-tags-tooltip
            placeholder="전체"
            clearable size="small"
            style="width: 220px"
            aria-label="기능 필터"
          >
            <el-option v-for="f in featureOptions" :key="f" :label="f" :value="f" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">업종 (Industry)</p>
          <el-select
            v-model="filter.industries"
            multiple collapse-tags collapse-tags-tooltip
            placeholder="전체"
            clearable size="small"
            style="width: 220px"
            aria-label="업종 필터"
          >
            <el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" />
          </el-select>
        </div>
        <el-button type="primary" size="small" @click="loadAll">적용</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 위젯 그리드 (12-column responsive) -->
    <div v-loading="store.widgetLoading" class="dashboard-grid">
      <el-empty
        v-if="!store.widgetLoading && store.widgets.length === 0"
        description="등록된 위젯이 없습니다"
      />
      <el-row v-else :gutter="16">
        <el-col
          v-for="w in activeWidgets"
          :key="w.id"
          :xs="24" :sm="24" :md="12" :lg="8" :xl="6"
          class="mb-4"
        >
          <el-card shadow="never" class="widget-card h-full" :body-style="{ padding: '16px' }">
            <div class="flex items-start justify-between mb-2">
              <div>
                <p class="text-sm font-semibold text-gray-700">{{ w.name }}</p>
                <p v-if="w.description" class="text-xs text-gray-400 mt-0.5">{{ w.description }}</p>
              </div>
              <el-tag size="small" :type="typeTagType(w.widget_type)">{{ w.widget_type }}</el-tag>
            </div>
            <div v-loading="store.widgetDataLoading[w.id]" class="widget-body">
              <component
                :is="renderWidget(w.widget_type)"
                :widget="w"
                :data="store.widgetDataMap[w.id]"
              />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 뷰 저장 다이얼로그 -->
    <el-dialog v-model="viewDialogVisible" title="현재 필터를 뷰로 저장" width="480px">
      <el-form :model="viewForm" label-width="100px">
        <el-form-item label="뷰 이름" required>
          <el-input v-model="viewForm.name" placeholder="기능별 7일 PV" />
        </el-form-item>
        <el-form-item label="설명">
          <el-input v-model="viewForm.description" type="textarea" :rows="2" placeholder="선택" />
        </el-form-item>
        <el-form-item label="공유">
          <el-switch v-model="viewForm.is_shared" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="viewDialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="savingView" @click="handleSaveView">저장</el-button>
      </template>
    </el-dialog>

    <!-- 내보내기 다이얼로그 -->
    <el-dialog v-model="exportDialogVisible" title="대시보드 내보내기" width="480px">
      <el-form label-width="100px">
        <el-form-item label="형식">
          <el-radio-group v-model="exportType">
            <el-radio value="EXCEL">EXCEL (.xlsx)</el-radio>
            <el-radio value="CSV">CSV</el-radio>
            <el-radio value="PDF">PDF</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="비동기 처리">
          <el-switch v-model="exportAsync" />
          <p class="text-xs text-gray-400 mt-1">대용량(10,000행 이상) 권장</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportDialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="exporting" @click="handleExport">요청</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, h, defineComponent } from 'vue'
import type { Component, PropType } from 'vue'
import { ElMessage } from 'element-plus'
import { Star, Download, Refresh } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart, RadarChart, HeatmapChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  VisualMapComponent,
  DatasetComponent,
} from 'echarts/components'
import { useDashboardStore } from '@/stores/dashboardStore'
import type {
  WidgetResponse,
  WidgetDataResponse,
  DashboardFilterState,
  ExportType,
} from '@/api/dashboard'

// @MX:NOTE: [AUTO] vue-echarts 컴포넌트 등록 — 9개 위젯 타입 중 차트 6종 사용
use([
  CanvasRenderer,
  BarChart,
  LineChart,
  PieChart,
  RadarChart,
  HeatmapChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  VisualMapComponent,
  DatasetComponent,
])

const store = useDashboardStore()

const filter = reactive<DashboardFilterState>({
  period: '7d',
  from: undefined,
  to: undefined,
  features: [],
  industries: [],
})

const featureOptions = ['board', 'policy', 'safety', 'media', 'content', 'system']
const industryOptions = ['IT', '제조업', '서비스업', '농업', '건설업', '유통']

const selectedViewId = ref<number | null>(null)
const viewDialogVisible = ref(false)
const exportDialogVisible = ref(false)
const savingView = ref(false)
const exporting = ref(false)

const viewForm = reactive({
  name: '',
  description: '',
  is_shared: false,
})

const exportType = ref<ExportType>('EXCEL')
const exportAsync = ref(false)

// 활성 위젯만 표시
const activeWidgets = computed(() =>
  store.widgets.filter(w => w.status === 'ACTIVE'),
)

async function loadAll(): Promise<void> {
  await store.fetchWidgets({ status: 'ACTIVE', size: 100 })
  // 위젯 데이터를 병렬로 로드 (max concurrent 6 — Promise.all + chunk)
  const widgetIds = activeWidgets.value.map(w => w.id)
  const chunkSize = 6
  for (let i = 0; i < widgetIds.length; i += chunkSize) {
    const chunk = widgetIds.slice(i, i + chunkSize)
    await Promise.all(chunk.map(id => loadWidgetData(id)))
  }
}

async function loadWidgetData(id: number): Promise<void> {
  try {
    const dim = filter.features?.length ? 'feature' : undefined
    const range = computeRange()
    await store.fetchWidgetData(id, {
      from: range.from,
      to: range.to,
      dim,
    })
  } catch {
    // 위젯 단위 실패는 무시 (다른 위젯 영향 X)
  }
}

interface DateRange {
  from?: string
  to?: string
}

function computeRange(): DateRange {
  if (filter.period === 'custom') {
    return { from: filter.from, to: filter.to }
  }
  const days = filter.period === '7d' ? 7 : filter.period === '30d' ? 30 : 90
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - days)
  return {
    from: from.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10),
  }
}

function resetFilter(): void {
  filter.period = '7d'
  filter.from = undefined
  filter.to = undefined
  filter.features = []
  filter.industries = []
  selectedViewId.value = null
  loadAll()
}

// ── 저장된 뷰 ──────────────────────────────────────────────────────────────
async function onApplyView(viewId: number | null): Promise<void> {
  if (!viewId) return
  try {
    const v = await store.applyView(viewId)
    // filter_state 는 JSON 문자열로 저장됨
    try {
      const parsed = JSON.parse(v.filter_state) as DashboardFilterState
      filter.period = parsed.period ?? '7d'
      filter.from = parsed.from
      filter.to = parsed.to
      filter.features = parsed.features ?? []
      filter.industries = parsed.industries ?? []
    } catch {
      ElMessage.warning('뷰 필터 파싱 실패: 기본 값으로 초기화')
    }
    await loadAll()
    ElMessage.success(`뷰 "${v.name}" 적용됨`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '뷰 적용 실패')
  }
}

function openSaveView(): void {
  viewForm.name = ''
  viewForm.description = ''
  viewForm.is_shared = false
  viewDialogVisible.value = true
}

async function handleSaveView(): Promise<void> {
  if (!viewForm.name.trim()) {
    ElMessage.warning('뷰 이름을 입력하세요')
    return
  }
  savingView.value = true
  try {
    const filterState = JSON.stringify(filter)
    await store.saveView({
      name: viewForm.name,
      description: viewForm.description || undefined,
      filter_state: filterState,
      is_shared: viewForm.is_shared,
    })
    ElMessage.success('뷰가 저장되었습니다')
    viewDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '뷰 저장 실패')
  } finally {
    savingView.value = false
  }
}

// ── 내보내기 ───────────────────────────────────────────────────────────────
function openExport(): void {
  exportType.value = 'EXCEL'
  exportAsync.value = false
  exportDialogVisible.value = true
}

async function handleExport(): Promise<void> {
  exporting.value = true
  try {
    const scope = JSON.stringify({
      filter,
      widget_ids: activeWidgets.value.map(w => w.id),
    })
    const res = await store.requestExport({
      export_type: exportType.value,
      scope,
      async: exportAsync.value,
    })
    ElMessage.success(`내보내기 요청 완료 (ID: ${res.id}, 상태: ${res.status})`)
    exportDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '내보내기 요청 실패')
  } finally {
    exporting.value = false
  }
}

// ── 위젯 렌더링 ────────────────────────────────────────────────────────────
function typeTagType(t: string): '' | 'success' | 'warning' | 'info' {
  if (t.endsWith('_CHART') || t === 'MATRIX_HEATMAP') return 'success'
  if (t === 'METRIC_CARD' || t === 'PROGRESS_BAR') return 'warning'
  return 'info'
}

function renderWidget(widgetType: string): Component {
  switch (widgetType) {
    case 'METRIC_CARD':
      return MetricCard
    case 'PROGRESS_BAR':
      return ProgressBarCard
    case 'TABLE':
      return TableCard
    case 'LINE_CHART':
    case 'BAR_CHART':
    case 'PIE_CHART':
    case 'RADAR_CHART':
    case 'MATRIX_HEATMAP':
    case 'MAP_KOREA':
      return ChartCard
    default:
      return EmptyCard
  }
}

// ── 위젯별 컴포넌트 (정의) ────────────────────────────────────────────────
const widgetProps = {
  widget: { type: Object as PropType<WidgetResponse>, required: true },
  data: { type: Object as PropType<WidgetDataResponse | undefined>, default: undefined },
} as const

const MetricCard = defineComponent({
  name: 'MetricCard',
  props: widgetProps,
  setup(props) {
    const value = computed(() => {
      const series = props.data?.dataset?.series ?? []
      if (!series.length || !series[0].data.length) return '—'
      const v = series[0].data[0]
      if (typeof v === 'number') return v.toLocaleString()
      return String(v ?? '—')
    })
    const label = computed(() => props.data?.dataset?.series?.[0]?.name ?? '')
    return () =>
      h('div', { class: 'flex flex-col items-center justify-center py-4' }, [
        h('p', { class: 'text-3xl font-bold text-blue-600' }, value.value),
        h('p', { class: 'text-xs text-gray-500 mt-1' }, label.value),
      ])
  },
})

const ProgressBarCard = defineComponent({
  name: 'ProgressBarCard',
  props: widgetProps,
  setup(props) {
    return () => {
      const series = props.data?.dataset?.series ?? []
      const categories = props.data?.dataset?.categories ?? []
      if (!series.length) return h('div', { class: 'text-gray-400 text-sm py-4' }, '데이터 없음')
      return h(
        'div',
        { class: 'flex flex-col gap-2 py-2' },
        categories.map((c, idx) => {
          const v = series[0]?.data[idx]
          const pct = typeof v === 'number' ? Math.min(100, Math.max(0, v)) : 0
          return h('div', { key: c }, [
            h('div', { class: 'flex justify-between text-xs mb-1' }, [
              h('span', null, c),
              h('span', { class: 'text-gray-500' }, `${pct}%`),
            ]),
            h('div', { class: 'h-2 bg-gray-200 rounded' }, [
              h('div', {
                class: 'h-2 bg-blue-500 rounded',
                style: { width: pct + '%' },
              }),
            ]),
          ])
        }),
      )
    }
  },
})

const TableCard = defineComponent({
  name: 'TableCard',
  props: widgetProps,
  setup(props) {
    return () => {
      const ds = props.data?.dataset
      if (!ds || !ds.series.length) return h('div', { class: 'text-gray-400 text-sm py-4' }, '데이터 없음')
      const header = ['항목', ...ds.series.map(s => s.name)]
      const rows = ds.categories.map((cat, idx) => [
        cat,
        ...ds.series.map(s => s.data[idx] ?? '-'),
      ])
      return h('div', { class: 'overflow-auto max-h-64' }, [
        h(
          'table',
          { class: 'w-full text-xs' },
          [
            h('thead', { class: 'bg-gray-50' }, [
              h(
                'tr',
                null,
                header.map(col =>
                  h('th', { class: 'px-2 py-1 text-left border-b' }, col),
                ),
              ),
            ]),
            h(
              'tbody',
              null,
              rows.map((r, ri) =>
                h(
                  'tr',
                  { key: ri },
                  r.map(cell =>
                    h('td', { class: 'px-2 py-1 border-b' }, String(cell ?? '-')),
                  ),
                ),
              ),
            ),
          ],
        ),
      ])
    }
  },
})

const ChartCard = defineComponent({
  name: 'ChartCard',
  components: { VChart },
  props: widgetProps,
  setup(props) {
    const option = computed(() => buildChartOption(props.widget, props.data))
    return () => {
      if (!props.data) return h('div', { class: 'text-gray-400 text-sm py-4 text-center' }, '데이터 없음')
      if (props.widget.widget_type === 'MAP_KOREA') {
        // 지도 지도 데이터가 등록되지 않은 1차 출시: placeholder
        return h(
          'div',
          { class: 'text-gray-400 text-xs py-8 text-center border border-dashed rounded' },
          '지도 위젯은 후속 릴리스에서 제공됩니다 (MAP_KOREA)',
        )
      }
      return h(VChart, {
        option: option.value,
        autoresize: true,
        style: { height: '240px', width: '100%' },
      })
    }
  },
})

const EmptyCard = defineComponent({
  name: 'EmptyCard',
  props: widgetProps,
  setup() {
    return () => h('div', { class: 'text-gray-400 text-sm py-4 text-center' }, '미지원 위젯 타입')
  },
})

// ECharts 옵션 빌더
interface ChartOption {
  title?: { text: string; show?: boolean }
  tooltip?: Record<string, unknown>
  legend?: Record<string, unknown>
  grid?: Record<string, unknown>
  xAxis?: Record<string, unknown>
  yAxis?: Record<string, unknown>
  series?: unknown[]
  radar?: Record<string, unknown>
  visualMap?: Record<string, unknown>
}

function buildChartOption(widget: WidgetResponse, data: WidgetDataResponse | undefined): ChartOption {
  if (!data) return {}
  const ds = data.dataset
  const palette = ['#1976d2', '#2e7d32', '#ed6c02', '#9c27b0', '#0288d1', '#d32f2f']
  switch (widget.widget_type) {
    case 'BAR_CHART':
      return {
        tooltip: { trigger: 'axis' },
        legend: { type: 'scroll', bottom: 0 },
        grid: { left: 40, right: 16, top: 16, bottom: 32 },
        xAxis: { type: 'category', data: ds.categories },
        yAxis: { type: 'value' },
        series: ds.series.map((s, i) => ({
          name: s.name,
          type: 'bar',
          data: s.data,
          itemStyle: { color: palette[i % palette.length] },
        })),
      }
    case 'LINE_CHART':
      return {
        tooltip: { trigger: 'axis' },
        legend: { type: 'scroll', bottom: 0 },
        grid: { left: 40, right: 16, top: 16, bottom: 32 },
        xAxis: { type: 'category', data: ds.categories, boundaryGap: false },
        yAxis: { type: 'value' },
        series: ds.series.map((s, i) => ({
          name: s.name,
          type: 'line',
          data: s.data,
          smooth: true,
          itemStyle: { color: palette[i % palette.length] },
        })),
      }
    case 'PIE_CHART': {
      const first = ds.series[0]
      const data1 = first ? ds.categories.map((c, i) => ({ name: c, value: first.data[i] })) : []
      return {
        tooltip: { trigger: 'item' },
        legend: { type: 'scroll', bottom: 0 },
        series: [
          {
            type: 'pie',
            radius: ['40%', '65%'],
            data: data1,
            itemStyle: { borderRadius: 4 },
            label: { fontSize: 11 },
          },
        ],
      }
    }
    case 'RADAR_CHART':
      return {
        tooltip: {},
        legend: { type: 'scroll', bottom: 0 },
        radar: {
          indicator: ds.categories.map(c => ({ name: c, max: 100 })),
        },
        series: [
          {
            type: 'radar',
            data: ds.series.map((s, i) => ({
              name: s.name,
              value: s.data,
              itemStyle: { color: palette[i % palette.length] },
            })),
          },
        ],
      }
    case 'MATRIX_HEATMAP': {
      // ds.series[0].data 가 [[xIdx, yIdx, value], ...] 형태라고 가정
      const yCats: string[] = ds.series.map(s => s.name)
      const heatmapData: Array<[number, number, number]> = []
      ds.series.forEach((s, yi) => {
        s.data.forEach((v, xi) => {
          if (typeof v === 'number') heatmapData.push([xi, yi, v])
        })
      })
      return {
        tooltip: { position: 'top' },
        grid: { left: 60, right: 16, top: 16, bottom: 40 },
        xAxis: { type: 'category', data: ds.categories, splitArea: { show: true } },
        yAxis: { type: 'category', data: yCats, splitArea: { show: true } },
        visualMap: { min: 0, max: 100, calculable: true, orient: 'horizontal', left: 'center', bottom: 0 },
        series: [
          {
            type: 'heatmap',
            data: heatmapData,
            label: { show: false },
          },
        ],
      }
    }
    default:
      return {}
  }
}

// ── 라이프사이클 ────────────────────────────────────────────────────────────
onMounted(async () => {
  await Promise.all([loadAll(), store.fetchViews()])
})

// 필터 변경 시 자동 재로드 비활성 — 명시적 "적용" 버튼만 트리거
watch(
  () => filter.period,
  (p) => {
    if (p !== 'custom') {
      filter.from = undefined
      filter.to = undefined
    }
  },
)
</script>

<style scoped>
.dashboard-grid {
  min-height: 400px;
}
.widget-card {
  transition: box-shadow 0.2s;
}
.widget-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.widget-body {
  min-height: 200px;
}
</style>
