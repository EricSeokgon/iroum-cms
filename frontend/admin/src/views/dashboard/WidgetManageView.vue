<template>
  <!-- 위젯 관리 (SUPER_ADMIN 전용) — SPEC-CMS-008 REQ-VIZ-001 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">위젯 관리</h2>
      <div class="flex gap-2">
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">
          위젯 등록
        </el-button>
      </div>
    </div>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">위젯 타입</p>
          <el-select v-model="filter.widget_type" clearable size="small" placeholder="전체" style="width: 180px">
            <el-option v-for="t in widgetTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">상태</p>
          <el-select v-model="filter.status" clearable size="small" placeholder="전체" style="width: 140px">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="INACTIVE" value="INACTIVE" />
            <el-option label="DEPRECATED" value="DEPRECATED" />
          </el-select>
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.widgetLoading">
      <el-table
        :data="store.widgets"
        stripe
        empty-text="등록된 위젯이 없습니다"
      >
        <el-table-column prop="code" label="코드" width="200" show-overflow-tooltip />
        <el-table-column prop="name" label="위젯명" min-width="200" show-overflow-tooltip />
        <el-table-column prop="widget_type" label="타입" width="160">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTagType(row.widget_type)">{{ row.widget_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="data_source" label="데이터 소스" width="140" show-overflow-tooltip />
        <el-table-column label="권한" width="200">
          <template #default="{ row }">
            <span v-if="row.required_role_codes?.length">
              {{ row.required_role_codes.slice(0, 2).join(', ') }}
              <span v-if="row.required_role_codes.length > 2" class="text-gray-400">
                +{{ row.required_role_codes.length - 2 }}
              </span>
            </span>
            <span v-else class="text-gray-400">전체</span>
          </template>
        </el-table-column>
        <el-table-column label="상태" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="200" align="center">
          <template #default="{ row }">
            <el-button v-if="isAdmin" link size="small" type="primary" @click="openEdit(row)">
              수정
            </el-button>
            <el-button v-if="isAdmin" link size="small" type="warning" @click="handlePreview(row)">
              미리보기
            </el-button>
            <el-button
              v-if="isAdmin && row.status !== 'DEPRECATED'"
              link size="small" type="danger"
              @click="handleDelete(row)"
            >
              비활성
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.widgets.length"
        layout="prev, pager, next, sizes, total"
        :page-sizes="[20, 50, 100]"
        class="mt-4 justify-end"
        @change="search"
      />
    </el-card>

    <!-- 위젯 등록/수정 다이얼로그 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '위젯 수정' : '위젯 등록'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px" label-position="right">
        <el-form-item label="코드" prop="code">
          <el-input v-model="form.code" placeholder="PV_BY_FEATURE" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="위젯명" prop="name">
          <el-input v-model="form.name" placeholder="기능별 PV" />
        </el-form-item>
        <el-form-item label="설명">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="위젯 설명" />
        </el-form-item>
        <el-form-item label="위젯 타입" prop="widget_type">
          <el-select v-model="form.widget_type" placeholder="선택" style="width: 100%">
            <el-option v-for="t in widgetTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="데이터 소스" prop="data_source">
          <el-select v-model="form.data_source" placeholder="선택" style="width: 100%">
            <el-option label="KPI_VALUE" value="KPI_VALUE" />
            <el-option label="CUSTOM_QUERY" value="CUSTOM_QUERY" />
            <el-option label="EXTERNAL_API" value="EXTERNAL_API" />
          </el-select>
        </el-form-item>
        <el-form-item label="데이터 소스 설정" prop="data_source_config">
          <el-input
            v-model="form.data_source_config"
            type="textarea"
            :rows="3"
            placeholder='{"kpi_id": 1, "metric": "PV"}'
          />
        </el-form-item>
        <el-form-item label="기본 설정">
          <el-input
            v-model="form.default_config"
            type="textarea"
            :rows="3"
            placeholder='{"period": "7d"}'
          />
        </el-form-item>
        <el-form-item label="사용 가능 차원">
          <el-select
            v-model="form.available_dimensions"
            multiple filterable allow-create
            placeholder="period, feature, industry 등"
            style="width: 100%"
          >
            <el-option v-for="d in dimensionOptions" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="필요 권한 (Role)">
          <el-select
            v-model="form.required_role_codes"
            multiple filterable allow-create
            placeholder="VIEWER, DEPT_ADMIN, SUPER_ADMIN"
            style="width: 100%"
          >
            <el-option label="VIEWER" value="VIEWER" />
            <el-option label="DEPT_ADMIN" value="DEPT_ADMIN" />
            <el-option label="SUPER_ADMIN" value="SUPER_ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="상태">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">ACTIVE</el-radio>
            <el-radio value="INACTIVE">INACTIVE</el-radio>
            <el-radio value="DEPRECATED">DEPRECATED</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ editingId ? '수정' : '등록' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 미리보기 다이얼로그 -->
    <el-dialog v-model="previewVisible" title="위젯 미리보기" width="640px">
      <div v-if="previewData">
        <p class="text-sm text-gray-600 mb-2">
          위젯: <strong>{{ previewData.widget.code }}</strong> ({{ previewData.widget.type }})
        </p>
        <p class="text-xs text-gray-400 mb-3">
          캐시 hit: {{ previewData.cache_hit ? 'YES' : 'NO' }} —
          생성 시각: {{ formatDate(previewData.generated_at) }}
        </p>
        <pre class="bg-gray-50 p-3 text-xs overflow-auto max-h-80">{{ JSON.stringify(previewData.dataset, null, 2) }}</pre>
      </div>
      <div v-else class="text-gray-400 text-sm">미리보기 데이터가 없습니다.</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboardStore'
import { useAuthStore } from '@/stores/auth'
import type {
  WidgetResponse,
  WidgetRequest,
  WidgetType,
  WidgetStatus,
  WidgetDataResponse,
} from '@/api/dashboard'

const store = useDashboardStore()
const auth = useAuthStore()

const isAdmin = computed(() =>
  (auth.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN'),
)

const widgetTypes: WidgetType[] = [
  'METRIC_CARD',
  'LINE_CHART',
  'BAR_CHART',
  'PIE_CHART',
  'RADAR_CHART',
  'MATRIX_HEATMAP',
  'TABLE',
  'PROGRESS_BAR',
  'MAP_KOREA',
]

const dimensionOptions = ['period', 'feature', 'industry', 'region', 'role', 'date']

interface FilterState {
  widget_type?: string
  status?: string
}

const filter = reactive<FilterState>({
  widget_type: undefined,
  status: undefined,
})

const page = ref(1)
const size = ref(20)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const previewData = ref<WidgetDataResponse | null>(null)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance | null>(null)

const form = reactive<WidgetRequest>({
  code: '',
  name: '',
  description: '',
  widget_type: 'METRIC_CARD',
  data_source: 'KPI_VALUE',
  data_source_config: '{}',
  default_config: '{}',
  available_dimensions: [],
  required_role_codes: [],
  status: 'ACTIVE',
})

const rules: FormRules<WidgetRequest> = {
  code: [{ required: true, message: '코드는 필수입니다', trigger: 'blur' }],
  name: [{ required: true, message: '위젯명은 필수입니다', trigger: 'blur' }],
  widget_type: [{ required: true, message: '위젯 타입은 필수입니다', trigger: 'change' }],
  data_source: [{ required: true, message: '데이터 소스는 필수입니다', trigger: 'change' }],
  data_source_config: [{ required: true, message: '데이터 소스 설정은 필수입니다', trigger: 'blur' }],
}

async function search(): Promise<void> {
  await store.fetchWidgets({
    widget_type: filter.widget_type,
    status: filter.status,
    page: page.value - 1,
    size: size.value,
  })
}

function resetFilter(): void {
  filter.widget_type = undefined
  filter.status = undefined
  page.value = 1
  search()
}

function resetForm(): void {
  form.code = ''
  form.name = ''
  form.description = ''
  form.widget_type = 'METRIC_CARD'
  form.data_source = 'KPI_VALUE'
  form.data_source_config = '{}'
  form.default_config = '{}'
  form.available_dimensions = []
  form.required_role_codes = []
  form.status = 'ACTIVE'
  editingId.value = null
}

function openCreate(): void {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: WidgetResponse): void {
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.description = row.description ?? ''
  form.widget_type = row.widget_type
  form.data_source = row.data_source
  form.data_source_config = row.data_source_config ?? '{}'
  form.default_config = row.default_config ?? '{}'
  form.available_dimensions = row.available_dimensions ?? []
  form.required_role_codes = row.required_role_codes ?? []
  form.status = row.status
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const req: WidgetRequest = { ...form }
    if (editingId.value) {
      await store.updateWidget(editingId.value, req)
      ElMessage.success('위젯이 수정되었습니다')
    } else {
      await store.createWidget(req)
      ElMessage.success('위젯이 등록되었습니다')
    }
    dialogVisible.value = false
    await search()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '위젯 저장 실패')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: WidgetResponse): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `위젯 "${row.name}"을(를) 비활성(DEPRECATED) 상태로 변경하시겠습니까?`,
      '위젯 비활성',
      { confirmButtonText: '실행', cancelButtonText: '취소', type: 'warning' },
    )
    await store.deleteWidget(row.id)
    ElMessage.success('위젯이 비활성 상태로 변경되었습니다')
    await search()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('비활성 처리 실패')
  }
}

async function handlePreview(row: WidgetResponse): Promise<void> {
  try {
    const req: WidgetRequest = {
      code: row.code,
      name: row.name,
      description: row.description,
      widget_type: row.widget_type,
      data_source: row.data_source,
      data_source_config: row.data_source_config ?? '{}',
      default_config: row.default_config,
      available_dimensions: row.available_dimensions,
      required_role_codes: row.required_role_codes,
      status: row.status,
    }
    const data = await store.previewWidget(req, ['SUPER_ADMIN'])
    previewData.value = data
    previewVisible.value = true
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '미리보기 실패')
  }
}

function statusTagType(s: WidgetStatus): '' | 'success' | 'info' | 'danger' {
  const map: Record<WidgetStatus, '' | 'success' | 'info' | 'danger'> = {
    ACTIVE: 'success',
    INACTIVE: 'info',
    DEPRECATED: 'danger',
  }
  return map[s] ?? ''
}

function typeTagType(t: string): '' | 'success' | 'warning' | 'info' {
  if (t.endsWith('_CHART') || t === 'MATRIX_HEATMAP') return 'success'
  if (t === 'METRIC_CARD' || t === 'PROGRESS_BAR') return 'warning'
  return 'info'
}

function formatDate(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(() => {
  search()
})
</script>
