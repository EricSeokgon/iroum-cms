<template>
  <!-- 데이터 표준 사전 — SPEC-CMS-009 REQ-GOV-001~005 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">데이터 표준 사전</h2>
      <div class="flex gap-2">
        <el-button :icon="Search" @click="handleFreshness" :loading="freshnessLoading">
          현행화 검사
        </el-button>
        <el-button :icon="Download" @click="handleExport('csv')">CSV 내보내기</el-button>
        <el-button :icon="Download" @click="handleExport('xlsx')">XLSX 내보내기</el-button>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">
          신규 등록
        </el-button>
      </div>
    </div>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">테이블명</p>
          <el-input v-model="filter.table" clearable size="small" placeholder="예: tb_user" style="width: 180px" />
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">데이터 도메인</p>
          <el-select v-model="filter.domain" clearable size="small" placeholder="전체" style="width: 140px">
            <el-option label="MASTER" value="MASTER" />
            <el-option label="TRANSACTION" value="TRANSACTION" />
            <el-option label="STATISTICS" value="STATISTICS" />
            <el-option label="LOG" value="LOG" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">상태</p>
          <el-select v-model="filter.status" clearable size="small" placeholder="전체" style="width: 130px">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DEPRECATED" value="DEPRECATED" />
            <el-option label="REMOVED" value="REMOVED" />
          </el-select>
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.dictionaryLoading">
      <el-table :data="store.dictionary" stripe empty-text="등록된 데이터 사전이 없습니다">
        <el-table-column prop="table_name" label="테이블" min-width="150" show-overflow-tooltip />
        <el-table-column prop="column_name" label="컬럼" min-width="140" show-overflow-tooltip />
        <el-table-column prop="logical_name_ko" label="논리명(한글)" min-width="160" show-overflow-tooltip />
        <el-table-column prop="data_domain" label="도메인" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="domainTagType(row.data_domain)">{{ row.data_domain }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="data_type" label="데이터 타입" width="130" />
        <el-table-column prop="is_pii" label="개인정보" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.is_pii" size="small" type="danger">PII</el-tag>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="상태" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="openHistory(row)">이력</el-button>
            <el-button v-if="isAdmin" size="small" link type="primary" @click="openEdit(row)">수정</el-button>
            <el-button v-if="isAdmin" size="small" link type="danger" @click="handleRemove(row)">삭제</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.dictionaryTotal"
        layout="prev, pager, next, sizes, total"
        :page-sizes="[20, 50, 100]"
        class="mt-4 justify-end"
        @change="search"
      />
    </el-card>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="테이블명" prop="table_name">
          <el-input v-model="form.table_name" :disabled="editMode" placeholder="예: tb_user" />
        </el-form-item>
        <el-form-item label="컬럼명" prop="column_name">
          <el-input v-model="form.column_name" :disabled="editMode" placeholder="예: user_id" />
        </el-form-item>
        <el-form-item label="논리명(한글)" prop="logical_name_ko">
          <el-input v-model="form.logical_name_ko" placeholder="예: 사용자ID" />
        </el-form-item>
        <el-form-item label="논리명(영문)">
          <el-input v-model="form.logical_name_en" placeholder="예: User ID" />
        </el-form-item>
        <el-form-item label="데이터 도메인" prop="data_domain">
          <el-select v-model="form.data_domain" style="width: 100%">
            <el-option label="MASTER" value="MASTER" />
            <el-option label="TRANSACTION" value="TRANSACTION" />
            <el-option label="STATISTICS" value="STATISTICS" />
            <el-option label="LOG" value="LOG" />
          </el-select>
        </el-form-item>
        <el-form-item label="데이터 타입" prop="data_type">
          <el-input v-model="form.data_type" placeholder="예: VARCHAR(50)" />
        </el-form-item>
        <el-form-item label="설명">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="옵션">
          <el-checkbox v-model="form.is_pii">개인정보(PII)</el-checkbox>
          <el-checkbox v-model="form.is_required">필수</el-checkbox>
        </el-form-item>
        <el-form-item label="상태" prop="status">
          <el-select v-model="form.status" style="width: 200px">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DEPRECATED" value="DEPRECATED" />
            <el-option label="REMOVED" value="REMOVED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">저장</el-button>
      </template>
    </el-dialog>

    <!-- 변경 이력 드로어 -->
    <el-drawer v-model="historyDrawerVisible" :title="historyTitle" size="40%">
      <div v-if="store.currentDictionary?.history?.length">
        <el-timeline>
          <el-timeline-item
            v-for="h in store.currentDictionary.history"
            :key="h.id"
            :timestamp="formatDateTime(h.changed_at)"
            placement="top"
          >
            <el-tag size="small" :type="changeTypeColor(h.change_type)">{{ h.change_type }}</el-tag>
            <span class="ml-2 text-xs text-gray-500">{{ h.changed_by ?? '시스템' }}</span>
            <pre v-if="h.after_json" class="mt-2 max-h-48 overflow-auto rounded bg-gray-50 p-2 text-xs">{{ formatJson(h.after_json) }}</pre>
          </el-timeline-item>
        </el-timeline>
      </div>
      <el-empty v-else description="변경 이력이 없습니다" />
    </el-drawer>

    <!-- 현행화 검사 결과 다이얼로그 -->
    <el-dialog v-model="freshnessDialogVisible" title="현행화 검사 결과" width="720px">
      <div v-if="store.freshness">
        <div class="mb-3 flex gap-3">
          <el-tag size="large">검사 대상 컬럼: {{ store.freshness.total_checked }}</el-tag>
          <el-tag size="large" type="warning">미등록: {{ store.freshness.missing.length }}</el-tag>
          <el-tag size="large" type="info">변경됨: {{ store.freshness.stale.length }}</el-tag>
        </div>
        <el-tabs>
          <el-tab-pane label="미등록 컬럼" :name="'missing'">
            <el-table :data="store.freshness.missing" max-height="320" empty-text="없음">
              <el-table-column prop="table_name" label="테이블" />
              <el-table-column prop="column_name" label="컬럼" />
              <el-table-column prop="data_type" label="DB 타입" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="변경된 컬럼" :name="'stale'">
            <el-table :data="store.freshness.stale" max-height="320" empty-text="없음">
              <el-table-column prop="table_name" label="테이블" />
              <el-table-column prop="column_name" label="컬럼" />
              <el-table-column prop="reason" label="사유" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Download, Search } from '@element-plus/icons-vue'
import { useGovernanceStore } from '@/stores/governanceStore'
import { useAuthStore } from '@/stores/auth'
import type {
  DataDictionary,
  DataDictionaryFilter,
  DataDictionaryRequest,
  DataDomain,
  DataDictionaryStatus,
} from '@/api/governance'

const store = useGovernanceStore()
const authStore = useAuthStore()
const isAdmin = computed(() =>
  (authStore.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

// 필터/페이지네이션
const filter = reactive<DataDictionaryFilter>({
  table: undefined,
  domain: undefined,
  status: undefined,
})
const page = ref(1)
const size = ref(20)

async function search(): Promise<void> {
  await store.fetchDictionary({ ...filter, page: page.value - 1, size: size.value })
}

function resetFilter(): void {
  filter.table = undefined
  filter.domain = undefined as DataDomain | undefined
  filter.status = undefined as DataDictionaryStatus | undefined
  page.value = 1
  search()
}

// 등록/수정
const dialogVisible = ref(false)
const editMode = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => (editMode.value ? '데이터 사전 수정' : '데이터 사전 등록'))

const form = reactive<DataDictionaryRequest>({
  table_name: '',
  column_name: '',
  logical_name_ko: '',
  logical_name_en: undefined,
  data_domain: 'MASTER',
  data_type: '',
  description: undefined,
  is_pii: false,
  is_required: false,
  status: 'ACTIVE',
})

const rules: FormRules = {
  table_name: [{ required: true, message: '테이블명을 입력하세요', trigger: 'blur' }],
  column_name: [{ required: true, message: '컬럼명을 입력하세요', trigger: 'blur' }],
  logical_name_ko: [{ required: true, message: '논리명(한글)을 입력하세요', trigger: 'blur' }],
  data_domain: [{ required: true, message: '도메인을 선택하세요', trigger: 'change' }],
  data_type: [{ required: true, message: '데이터 타입을 입력하세요', trigger: 'blur' }],
  status: [{ required: true, message: '상태를 선택하세요', trigger: 'change' }],
}

function resetForm(): void {
  form.table_name = ''
  form.column_name = ''
  form.logical_name_ko = ''
  form.logical_name_en = undefined
  form.data_domain = 'MASTER'
  form.data_type = ''
  form.description = undefined
  form.is_pii = false
  form.is_required = false
  form.status = 'ACTIVE'
}

function openCreate(): void {
  editMode.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: DataDictionary): void {
  editMode.value = true
  editingId.value = row.id
  form.table_name = row.table_name
  form.column_name = row.column_name
  form.logical_name_ko = row.logical_name_ko
  form.logical_name_en = row.logical_name_en
  form.data_domain = row.data_domain
  form.data_type = row.data_type
  form.description = row.description
  form.is_pii = row.is_pii
  form.is_required = row.is_required
  form.status = row.status
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editMode.value && editingId.value) {
      await store.updateDictionary(editingId.value, { ...form })
      ElMessage.success('수정되었습니다')
    } else {
      await store.createDictionary({ ...form })
      ElMessage.success('등록되었습니다')
    }
    dialogVisible.value = false
    await search()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '저장 실패')
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: DataDictionary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `${row.table_name}.${row.column_name} 항목을 삭제(REMOVED)하시겠습니까?`,
      '삭제 확인',
      { confirmButtonText: '삭제', cancelButtonText: '취소', type: 'warning' },
    )
    await store.removeDictionary(row.id)
    ElMessage.success('삭제되었습니다')
    await search()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('삭제 실패')
  }
}

// 변경 이력 드로어
const historyDrawerVisible = ref(false)
const historyTitle = ref('')

async function openHistory(row: DataDictionary): Promise<void> {
  historyTitle.value = `${row.table_name}.${row.column_name} 변경 이력`
  await store.fetchDictionaryDetail(row.id)
  historyDrawerVisible.value = true
}

// 현행화 검사
const freshnessLoading = ref(false)
const freshnessDialogVisible = ref(false)

async function handleFreshness(): Promise<void> {
  freshnessLoading.value = true
  try {
    await store.fetchFreshness()
    freshnessDialogVisible.value = true
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '현행화 검사 실패')
  } finally {
    freshnessLoading.value = false
  }
}

// 내보내기
async function handleExport(format: 'csv' | 'xlsx'): Promise<void> {
  try {
    const blob = await store.exportDictionary(format)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `data-dictionary-${new Date().toISOString().slice(0, 10)}.${format}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    ElMessage.success(`${format.toUpperCase()} 다운로드를 시작합니다`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '내보내기 실패')
  }
}

// 헬퍼
function domainTagType(d: DataDomain): '' | 'success' | 'warning' | 'info' {
  const map: Record<DataDomain, '' | 'success' | 'warning' | 'info'> = {
    MASTER: '',
    TRANSACTION: 'success',
    STATISTICS: 'warning',
    LOG: 'info',
  }
  return map[d]
}

function statusTagType(s: DataDictionaryStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'ACTIVE') return 'success'
  if (s === 'DEPRECATED') return 'warning'
  return 'info'
}

function changeTypeColor(t: 'CREATE' | 'UPDATE' | 'DELETE'): 'success' | 'warning' | 'danger' {
  if (t === 'CREATE') return 'success'
  if (t === 'UPDATE') return 'warning'
  return 'danger'
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

function formatJson(s: string): string {
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

onMounted(() => {
  search()
})
</script>
