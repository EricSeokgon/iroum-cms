<template>
  <!-- 품질 룰 관리 — SPEC-CMS-009 REQ-DATA-006 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">데이터 품질 룰</h2>
      <div class="flex gap-2">
        <el-button :icon="Refresh" @click="search">새로고침</el-button>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">신규 등록</el-button>
      </div>
    </div>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">룰 유형</p>
          <el-select v-model="filter.ruleType" clearable size="small" placeholder="전체" style="width: 160px">
            <el-option label="NULL_RATIO" value="NULL_RATIO" />
            <el-option label="RANGE" value="RANGE" />
            <el-option label="IQR" value="IQR" />
            <el-option label="UNIQUE" value="UNIQUE" />
            <el-option label="FRESHNESS" value="FRESHNESS" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">심각도</p>
          <el-select v-model="filter.severity" clearable size="small" placeholder="전체" style="width: 130px">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="CRITICAL" value="CRITICAL" />
          </el-select>
        </div>
        <div>
          <p class="mb-1 text-xs text-gray-500">활성 상태</p>
          <el-switch
            v-model="filter.active"
            active-text="ACTIVE"
            inactive-text="ALL"
          />
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.qualityRulesLoading">
      <el-table :data="store.qualityRules" stripe empty-text="등록된 품질 룰이 없습니다">
        <el-table-column label="대상" min-width="220">
          <template #default="{ row }">
            <span class="font-mono text-xs">{{ row.target_table }}.{{ row.target_column }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rule_type" label="룰 유형" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.rule_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="기준" min-width="160">
          <template #default="{ row }">{{ formatThreshold(row) }}</template>
        </el-table-column>
        <el-table-column prop="severity" label="심각도" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="severityTagType(row.severity)">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="schedule_cron" label="스케줄" min-width="160">
          <template #default="{ row }"><code class="text-xs">{{ row.schedule_cron }}</code></template>
        </el-table-column>
        <el-table-column prop="status" label="상태" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isAdmin" size="small" link type="primary" @click="openEdit(row)">수정</el-button>
            <el-button v-if="isAdmin" size="small" link type="warning" @click="handleRunNow(row)">즉시 실행</el-button>
            <el-button v-if="isAdmin" size="small" link type="danger" @click="handleRemove(row)">삭제</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.qualityRulesTotal"
        layout="prev, pager, next, sizes, total"
        :page-sizes="[20, 50, 100]"
        class="mt-4 justify-end"
        @change="search"
      />
    </el-card>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="대상 테이블" prop="target_table">
          <el-input v-model="form.target_table" :disabled="editMode" placeholder="예: tb_user" />
        </el-form-item>
        <el-form-item label="대상 컬럼" prop="target_column">
          <el-input v-model="form.target_column" :disabled="editMode" placeholder="예: email" />
        </el-form-item>
        <el-form-item label="룰 유형" prop="rule_type">
          <el-select v-model="form.rule_type" style="width: 100%">
            <el-option label="NULL_RATIO (NULL 비율)" value="NULL_RATIO" />
            <el-option label="RANGE (범위)" value="RANGE" />
            <el-option label="IQR (이상치 IQR)" value="IQR" />
            <el-option label="UNIQUE (고유값 비율)" value="UNIQUE" />
            <el-option label="FRESHNESS (최신성)" value="FRESHNESS" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.rule_type !== 'RANGE'" label="임계값" prop="threshold">
          <el-input-number v-model="form.threshold" :precision="4" :step="0.01" />
          <p class="ml-2 inline text-xs text-gray-400">예: 0.05 (5%), 24 (시간)</p>
        </el-form-item>
        <template v-if="form.rule_type === 'RANGE'">
          <el-form-item label="최솟값" prop="range_min">
            <el-input-number v-model="form.range_min" :precision="4" />
          </el-form-item>
          <el-form-item label="최댓값" prop="range_max">
            <el-input-number v-model="form.range_max" :precision="4" />
          </el-form-item>
        </template>
        <el-form-item label="심각도" prop="severity">
          <el-radio-group v-model="form.severity">
            <el-radio value="INFO">INFO</el-radio>
            <el-radio value="WARN">WARN</el-radio>
            <el-radio value="CRITICAL">CRITICAL</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="스케줄" prop="schedule_cron">
          <el-input v-model="form.schedule_cron" placeholder="0 0 3 * * ?" />
          <p class="mt-1 text-xs text-gray-400">Spring 6-필드 cron 형식</p>
        </el-form-item>
        <el-form-item label="상태" prop="status">
          <el-select v-model="form.status" style="width: 200px">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="INACTIVE" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">저장</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useGovernanceStore } from '@/stores/governanceStore'
import { useAuthStore } from '@/stores/auth'
import type {
  QualityRule,
  QualityRuleFilter,
  QualityRuleRequest,
  QualityRuleType,
  QualitySeverity,
} from '@/api/governance'

const store = useGovernanceStore()
const authStore = useAuthStore()
const isAdmin = computed(() =>
  (authStore.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

const cronRegex = /^\s*\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s*$/

// 필터/페이지
const filter = reactive<QualityRuleFilter>({
  ruleType: undefined,
  severity: undefined,
  active: false,
})
const page = ref(1)
const size = ref(20)

async function search(): Promise<void> {
  await store.fetchQualityRules({ ...filter, page: page.value - 1, size: size.value })
}

function resetFilter(): void {
  filter.ruleType = undefined as QualityRuleType | undefined
  filter.severity = undefined as QualitySeverity | undefined
  filter.active = false
  page.value = 1
  search()
}

// 등록/수정
const dialogVisible = ref(false)
const editMode = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => (editMode.value ? '품질 룰 수정' : '품질 룰 등록'))

const form = reactive<QualityRuleRequest>({
  target_table: '',
  target_column: '',
  rule_type: 'NULL_RATIO',
  threshold: 0.05,
  range_min: undefined,
  range_max: undefined,
  severity: 'WARN',
  schedule_cron: '0 0 3 * * ?',
  status: 'ACTIVE',
})

const rules: FormRules = {
  target_table: [{ required: true, message: '대상 테이블을 입력하세요', trigger: 'blur' }],
  target_column: [{ required: true, message: '대상 컬럼을 입력하세요', trigger: 'blur' }],
  rule_type: [{ required: true, message: '룰 유형을 선택하세요', trigger: 'change' }],
  threshold: [
    {
      validator: (_rule, value: number | undefined, callback) => {
        if (form.rule_type !== 'RANGE' && (value === undefined || value === null)) {
          callback(new Error('임계값을 입력하세요'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  range_min: [
    {
      validator: (_rule, value: number | undefined, callback) => {
        if (form.rule_type === 'RANGE' && (value === undefined || value === null)) {
          callback(new Error('최솟값을 입력하세요'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  range_max: [
    {
      validator: (_rule, value: number | undefined, callback) => {
        if (form.rule_type === 'RANGE' && (value === undefined || value === null)) {
          callback(new Error('최댓값을 입력하세요'))
        } else if (
          form.rule_type === 'RANGE' &&
          form.range_min !== undefined &&
          value !== undefined &&
          value < form.range_min
        ) {
          callback(new Error('최댓값은 최솟값보다 커야 합니다'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  severity: [{ required: true, message: '심각도를 선택하세요', trigger: 'change' }],
  schedule_cron: [
    { required: true, message: 'cron 식을 입력하세요', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value || !cronRegex.test(value)) {
          callback(new Error('Spring 6-필드 cron 형식이어야 합니다'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  status: [{ required: true, message: '상태를 선택하세요', trigger: 'change' }],
}

function resetForm(): void {
  form.target_table = ''
  form.target_column = ''
  form.rule_type = 'NULL_RATIO'
  form.threshold = 0.05
  form.range_min = undefined
  form.range_max = undefined
  form.severity = 'WARN'
  form.schedule_cron = '0 0 3 * * ?'
  form.status = 'ACTIVE'
}

function openCreate(): void {
  editMode.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: QualityRule): void {
  editMode.value = true
  editingId.value = row.id
  form.target_table = row.target_table
  form.target_column = row.target_column
  form.rule_type = row.rule_type
  form.threshold = row.threshold
  form.range_min = row.range_min
  form.range_max = row.range_max
  form.severity = row.severity
  form.schedule_cron = row.schedule_cron
  form.status = row.status
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload: QualityRuleRequest = { ...form }
    if (form.rule_type === 'RANGE') {
      payload.threshold = undefined
    } else {
      payload.range_min = undefined
      payload.range_max = undefined
    }

    if (editMode.value && editingId.value) {
      await store.updateQualityRule(editingId.value, payload)
      ElMessage.success('수정되었습니다')
    } else {
      await store.createQualityRule(payload)
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

async function handleRemove(row: QualityRule): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `${row.target_table}.${row.target_column} 룰을 삭제하시겠습니까?`,
      '삭제 확인',
      { confirmButtonText: '삭제', cancelButtonText: '취소', type: 'warning' },
    )
    await store.removeQualityRule(row.id)
    ElMessage.success('삭제되었습니다')
    await search()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('삭제 실패')
  }
}

async function handleRunNow(row: QualityRule): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `${row.target_table}.${row.target_column} 룰을 즉시 실행하시겠습니까?`,
      '실행 확인',
      { confirmButtonText: '실행', cancelButtonText: '취소', type: 'warning' },
    )
    const res = await store.runQualityRule(row.id)
    ElMessage.success(`실행 시작 (배치 로그 ID: ${res.id}, 상태: ${res.status})`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e instanceof Error ? e.message : '실행 실패')
  }
}

// 헬퍼
function severityTagType(s: QualitySeverity): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'CRITICAL') return 'danger'
  if (s === 'WARN') return 'warning'
  return 'info'
}

function formatThreshold(row: QualityRule): string {
  if (row.rule_type === 'RANGE') {
    return `[${row.range_min ?? '-'}, ${row.range_max ?? '-'}]`
  }
  return row.threshold !== undefined ? String(row.threshold) : '-'
}

onMounted(() => {
  search()
})
</script>
