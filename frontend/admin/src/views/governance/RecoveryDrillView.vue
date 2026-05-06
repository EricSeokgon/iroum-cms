<template>
  <!-- 복구 시험 — SPEC-CMS-009 REQ-GOV-011, REQ-GOV-012 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">백업/복구 시험</h2>
      <div class="flex items-center gap-3">
        <!-- 백업 상태 배지 -->
        <div
          v-if="store.backupStatus"
          class="flex items-center gap-2 rounded border px-3 py-1 text-xs"
          :class="store.backupStatus.rpo_compliance ? 'border-green-300 bg-green-50' : 'border-red-300 bg-red-50'"
        >
          <span
            class="inline-block h-2 w-2 rounded-full"
            :class="store.backupStatus.rpo_compliance ? 'bg-green-500' : 'bg-red-500'"
          />
          <span class="font-medium">백업:</span>
          <span>{{ formatRelative(store.backupStatus.last_backup_at) }}</span>
          <span class="text-gray-500">(목표 RPO: {{ store.backupStatus.target_rpo_min }}분)</span>
        </div>
        <el-button :icon="Refresh" @click="reload">새로고침</el-button>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">신규 등록</el-button>
      </div>
    </div>

    <!-- 다음 권장 시험일 안내 -->
    <el-card v-if="lastDrill" class="mb-4" shadow="never">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-gray-700">
            마지막 시험: <strong>{{ lastDrill.drill_date }}</strong>
            ({{ lastDrill.drill_type }} / {{ lastDrill.result }})
          </p>
          <p class="mt-1 text-xs text-gray-500">
            다음 권장 시험일: <strong>{{ nextRecommendedDate }}</strong>
          </p>
        </div>
        <el-tag v-if="overdue" type="danger">권장일 경과</el-tag>
        <el-tag v-else type="success">정상</el-tag>
      </div>
    </el-card>

    <!-- 검색 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap gap-3 items-end">
        <div>
          <p class="mb-1 text-xs text-gray-500">기간</p>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="~"
            start-placeholder="시작"
            end-placeholder="종료"
            value-format="YYYY-MM-DD"
            size="small"
          />
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
        <el-button size="small" @click="resetFilter">초기화</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.recoveryDrillsLoading">
      <el-table :data="store.recoveryDrills" stripe empty-text="시험 이력이 없습니다">
        <el-table-column prop="drill_date" label="시험일" width="120" />
        <el-table-column prop="drill_type" label="유형" width="160">
          <template #default="{ row }">
            <el-tag size="small">{{ row.drill_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="결과" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.result === 'PASS' ? 'success' : 'danger'">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="RTO (실제 / 목표)" min-width="160">
          <template #default="{ row }">
            <span :class="row.rto_actual_min > row.rto_target_min ? 'text-red-600' : 'text-green-600'">
              {{ row.rto_actual_min }}분
            </span>
            <span class="text-gray-400 mx-1">/ {{ row.rto_target_min }}분</span>
            <el-tag
              v-if="row.rto_actual_min > row.rto_target_min"
              size="small"
              type="danger"
              class="ml-2"
            >
              초과
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="RPO (실제 / 목표)" min-width="160">
          <template #default="{ row }">
            <span :class="row.rpo_actual_min > row.rpo_target_min ? 'text-red-600' : 'text-green-600'">
              {{ row.rpo_actual_min }}분
            </span>
            <span class="text-gray-400 mx-1">/ {{ row.rpo_target_min }}분</span>
          </template>
        </el-table-column>
        <el-table-column prop="notes" label="비고" min-width="160" show-overflow-tooltip />
        <el-table-column label="작업" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="openDetail(row)">상세</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 신규 등록 다이얼로그 -->
    <el-dialog v-model="dialogVisible" title="복구 시험 등록" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="시험일" prop="drill_date">
          <el-date-picker
            v-model="form.drill_date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="날짜 선택"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="시험 유형" prop="drill_type">
          <el-select v-model="form.drill_type" style="width: 100%">
            <el-option label="BACKUP_RESTORE (백업 복원)" value="BACKUP_RESTORE" />
            <el-option label="FAILOVER (페일오버)" value="FAILOVER" />
            <el-option label="FULL_DR (전체 재해 복구)" value="FULL_DR" />
          </el-select>
        </el-form-item>
        <el-form-item label="결과" prop="result">
          <el-radio-group v-model="form.result">
            <el-radio value="PASS">PASS</el-radio>
            <el-radio value="FAIL">FAIL</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="실제 RTO (분)" prop="rto_actual_min">
          <el-input-number v-model="form.rto_actual_min" :min="0" :max="1440" />
          <p class="ml-2 inline text-xs text-gray-400">목표: 240분 (자동)</p>
        </el-form-item>
        <el-form-item label="실제 RPO (분)" prop="rpo_actual_min">
          <el-input-number v-model="form.rpo_actual_min" :min="0" :max="1440" />
          <p class="ml-2 inline text-xs text-gray-400">목표: 60분 (자동)</p>
        </el-form-item>
        <el-form-item label="체크리스트">
          <div class="grid grid-cols-1 gap-2 w-full">
            <el-checkbox
              v-for="item in checklistItems"
              :key="item.key"
              v-model="form.checklist_json[item.key]"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-form-item>
        <el-form-item label="비고">
          <el-input v-model="form.notes" type="textarea" :rows="3" placeholder="시험 결과 메모" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">저장</el-button>
      </template>
    </el-dialog>

    <!-- 상세 드로어 -->
    <el-drawer v-model="detailDrawerVisible" title="복구 시험 상세" size="38%">
      <div v-if="selectedDrill">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="시험일">{{ selectedDrill.drill_date }}</el-descriptions-item>
          <el-descriptions-item label="시험 유형">{{ selectedDrill.drill_type }}</el-descriptions-item>
          <el-descriptions-item label="결과">
            <el-tag :type="selectedDrill.result === 'PASS' ? 'success' : 'danger'">{{ selectedDrill.result }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="RTO 실제 / 목표">
            {{ selectedDrill.rto_actual_min }}분 / {{ selectedDrill.rto_target_min }}분
          </el-descriptions-item>
          <el-descriptions-item label="RPO 실제 / 목표">
            {{ selectedDrill.rpo_actual_min }}분 / {{ selectedDrill.rpo_target_min }}분
          </el-descriptions-item>
          <el-descriptions-item label="비고">{{ selectedDrill.notes || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="mt-4">
          <p class="mb-2 text-sm font-semibold">체크리스트</p>
          <div class="rounded border bg-gray-50 p-3">
            <div
              v-for="item in checklistItems"
              :key="item.key"
              class="flex items-center gap-2 py-1 text-sm"
            >
              <el-icon :color="(selectedDrill.checklist_json?.[item.key]) ? '#67c23a' : '#909399'">
                <component :is="(selectedDrill.checklist_json?.[item.key]) ? Check : Close" />
              </el-icon>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh, Check, Close } from '@element-plus/icons-vue'
import { useGovernanceStore } from '@/stores/governanceStore'
import { useAuthStore } from '@/stores/auth'
import type { RecoveryDrill, RecoveryDrillRequest } from '@/api/governance'

const store = useGovernanceStore()
const authStore = useAuthStore()
const isAdmin = computed(() =>
  (authStore.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

// 체크리스트 항목 정의
const checklistItems: Array<{ key: string; label: string }> = [
  { key: 'db_restore', label: 'DB 복원 절차 검증' },
  { key: 'app_smoke', label: '애플리케이션 스모크 테스트' },
  { key: 'data_verify', label: '데이터 무결성 검증' },
  { key: 'network', label: '네트워크 연결성 확인' },
  { key: 'backup_integrity', label: '백업 파일 무결성' },
  { key: 'rto_meets_target', label: 'RTO 목표 충족' },
  { key: 'rpo_meets_target', label: 'RPO 목표 충족' },
]

// 필터
const dateRange = ref<[string, string] | null>(null)

async function search(): Promise<void> {
  const params: { from?: string; to?: string } = {}
  if (dateRange.value) {
    params.from = dateRange.value[0]
    params.to = dateRange.value[1]
  }
  await store.fetchRecoveryDrills(params)
}

function resetFilter(): void {
  dateRange.value = null
  search()
}

async function reload(): Promise<void> {
  await Promise.all([search(), store.fetchBackupStatus()])
}

// 마지막 시험 / 다음 권장일
const lastDrill = computed<RecoveryDrill | null>(() =>
  store.recoveryDrills.length > 0 ? store.recoveryDrills[0] : null,
)

const nextRecommendedDate = computed<string>(() => {
  if (!lastDrill.value) return '-'
  const last = new Date(lastDrill.value.drill_date)
  last.setDate(last.getDate() + 90)
  return last.toISOString().slice(0, 10)
})

const overdue = computed<boolean>(() => {
  if (!lastDrill.value) return false
  const next = new Date(nextRecommendedDate.value)
  return next.getTime() < Date.now()
})

// 등록
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

function buildDefaultChecklist(): Record<string, boolean> {
  const obj: Record<string, boolean> = {}
  checklistItems.forEach(it => { obj[it.key] = false })
  return obj
}

const form = reactive<RecoveryDrillRequest>({
  drill_date: new Date().toISOString().slice(0, 10),
  drill_type: 'BACKUP_RESTORE',
  result: 'PASS',
  rto_actual_min: 0,
  rpo_actual_min: 0,
  checklist_json: buildDefaultChecklist(),
  notes: undefined,
})

const rules: FormRules = {
  drill_date: [{ required: true, message: '시험일을 선택하세요', trigger: 'change' }],
  drill_type: [{ required: true, message: '시험 유형을 선택하세요', trigger: 'change' }],
  result: [{ required: true, message: '결과를 선택하세요', trigger: 'change' }],
  rto_actual_min: [{ required: true, message: '실제 RTO를 입력하세요', trigger: 'blur' }],
  rpo_actual_min: [{ required: true, message: '실제 RPO를 입력하세요', trigger: 'blur' }],
}

function openCreate(): void {
  form.drill_date = new Date().toISOString().slice(0, 10)
  form.drill_type = 'BACKUP_RESTORE'
  form.result = 'PASS'
  form.rto_actual_min = 0
  form.rpo_actual_min = 0
  form.checklist_json = buildDefaultChecklist()
  form.notes = undefined
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await store.createRecoveryDrill({ ...form })
    ElMessage.success('복구 시험이 등록되었습니다')
    dialogVisible.value = false
    await reload()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '저장 실패')
  } finally {
    submitting.value = false
  }
}

// 상세
const detailDrawerVisible = ref(false)
const selectedDrill = ref<RecoveryDrill | null>(null)

function openDetail(row: RecoveryDrill): void {
  selectedDrill.value = row
  detailDrawerVisible.value = true
}

// 헬퍼
function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime()
  const hours = Math.floor(diffMs / (1000 * 60 * 60))
  if (hours < 1) return '1시간 미만 전'
  if (hours < 24) return `${hours}시간 전`
  const days = Math.floor(hours / 24)
  return `${days}일 전`
}

onMounted(() => {
  reload()
})
</script>
