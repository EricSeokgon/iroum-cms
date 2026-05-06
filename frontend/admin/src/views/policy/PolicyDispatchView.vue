<template>
  <!-- 발송 예약 관리 (Admin) — SPEC-CMS-007 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">알림 발송 예약 관리</h2>
      <div class="flex gap-2">
        <el-button :icon="Refresh" @click="search">새로고침</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">새 예약</el-button>
      </div>
    </div>

    <!-- 필터 -->
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <p class="mb-1 text-xs text-gray-500">상태</p>
          <el-select v-model="statusFilter" clearable size="small" placeholder="전체" style="width: 160px">
            <el-option label="PENDING" value="PENDING" />
            <el-option label="PROCESSING" value="PROCESSING" />
            <el-option label="COMPLETED" value="COMPLETED" />
            <el-option label="CANCELLED" value="CANCELLED" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </div>
        <el-button type="primary" size="small" @click="search">검색</el-button>
      </div>
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.scheduleLoading">
      <el-table :data="store.schedules" stripe empty-text="예약된 발송이 없습니다">
        <el-table-column prop="policy_title" label="정책" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.policy_title">{{ row.policy_title }}</span>
            <span v-else class="text-gray-400">(없음)</span>
          </template>
        </el-table-column>
        <el-table-column prop="dispatch_type" label="유형" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.dispatch_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="채널" width="200">
          <template #default="{ row }">
            <el-tag
              v-for="ch in row.channels"
              :key="ch"
              size="small"
              type="info"
              class="mr-1"
            >
              {{ ch }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="예약 시각" width="180">
          <template #default="{ row }">
            {{ formatDate(row.scheduled_at) }}
          </template>
        </el-table-column>
        <el-table-column label="대상 수" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.total_targets !== undefined">{{ row.total_targets.toLocaleString() }}</span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="상태" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              size="small"
              type="success"
              link
              @click="handleTrigger(row)"
            >
              즉시 발송
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              size="small"
              type="danger"
              link
              @click="handleCancel(row)"
            >
              취소
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="store.schedulesTotal"
        layout="prev, pager, next, total"
        :page-sizes="[20, 50]"
        class="mt-4 justify-end"
        @change="search"
      />
    </el-card>

    <!-- 새 예약 다이얼로그 -->
    <el-dialog v-model="dialogOpen" title="발송 예약 생성" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="정책">
          <el-select
            v-model="form.policy_id"
            filterable
            remote
            placeholder="정책 선택 (선택사항)"
            :remote-method="searchPolicies"
            style="width: 100%"
          >
            <el-option
              v-for="p in policyOptions"
              :key="p.id"
              :label="p.title"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="템플릿 ID" prop="template_id">
          <el-input-number v-model="form.template_id" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="발송 유형" prop="dispatch_type">
          <el-select v-model="form.dispatch_type" style="width: 100%">
            <el-option label="POLICY_MATCH" value="POLICY_MATCH" />
            <el-option label="ANNOUNCEMENT" value="ANNOUNCEMENT" />
            <el-option label="REMINDER" value="REMINDER" />
            <el-option label="MARKETING" value="MARKETING" />
          </el-select>
        </el-form-item>
        <el-form-item label="채널" prop="channels">
          <el-checkbox-group v-model="form.channels">
            <el-checkbox label="KAKAO" value="KAKAO" />
            <el-checkbox label="EMAIL" value="EMAIL" />
            <el-checkbox label="INAPP" value="INAPP" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="예약 시각" prop="scheduled_at">
          <el-date-picker
            v-model="form.scheduled_at"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="발송 시각"
            style="width: 100%"
          />
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="야간 발송 안내"
          description="야간(21:00~08:00 KST) 예약 시 익일 09:00로 자동 조정됩니다."
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">취소</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">생성</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { usePolicyStore } from '@/stores/policyStore'
import { policyApi } from '@/api/policy'
import type {
  Channel,
  DispatchScheduleRequest,
  DispatchScheduleSummary,
  DispatchStatus,
  DispatchType,
  PolicyProgramSummary,
} from '@/api/policy'

const store = usePolicyStore()

const statusFilter = ref<DispatchStatus | undefined>(undefined)
const page = ref(1)
const size = ref(20)

const dialogOpen = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<DispatchScheduleRequest>({
  policy_id: undefined,
  template_id: 1,
  dispatch_type: 'POLICY_MATCH' as DispatchType,
  channels: [] as Channel[],
  scheduled_at: '',
  target_filter: undefined,
})

const policyOptions = ref<PolicyProgramSummary[]>([])

const rules: FormRules = {
  template_id: [{ required: true, message: '템플릿 ID를 선택하세요', trigger: 'blur' }],
  dispatch_type: [{ required: true, message: '발송 유형을 선택하세요', trigger: 'change' }],
  channels: [
    {
      required: true,
      type: 'array',
      min: 1,
      message: '채널을 1개 이상 선택하세요',
      trigger: 'change',
    },
  ],
  scheduled_at: [{ required: true, message: '예약 시각을 선택하세요', trigger: 'change' }],
}

async function search(): Promise<void> {
  await store.fetchSchedules({
    page: page.value - 1,
    size: size.value,
    status: statusFilter.value,
  })
}

function openCreate(): void {
  form.policy_id = undefined
  form.template_id = 1
  form.dispatch_type = 'POLICY_MATCH'
  form.channels = []
  form.scheduled_at = ''
  form.target_filter = undefined
  dialogOpen.value = true
}

async function handleCreate(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await store.createSchedule(form)
    ElMessage.success('발송 예약이 생성되었습니다')
    dialogOpen.value = false
    await search()
  } catch {
    ElMessage.error('예약 생성 실패')
  } finally {
    creating.value = false
  }
}

async function handleTrigger(row: DispatchScheduleSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `예약 ${row.uuid}를 즉시 발송하시겠습니까? 이 작업은 취소할 수 없습니다.`,
      '즉시 발송',
      { confirmButtonText: '발송', cancelButtonText: '취소', type: 'warning' },
    )
    await store.triggerSchedule(row.uuid)
    ElMessage.success('발송이 시작되었습니다')
    await search()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('발송 트리거 실패')
  }
}

async function handleCancel(row: DispatchScheduleSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(`예약 ${row.uuid}를 취소하시겠습니까?`, '예약 취소', {
      confirmButtonText: '예약 취소',
      cancelButtonText: '닫기',
    })
    await store.cancelSchedule(row.uuid)
    ElMessage.success('예약이 취소되었습니다')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('예약 취소 실패')
  }
}

async function searchPolicies(query: string): Promise<void> {
  if (!query) {
    policyOptions.value = []
    return
  }
  try {
    const res = await policyApi.programs.list({ search: query, page: 0, size: 20 })
    policyOptions.value = res.data.content
  } catch {
    policyOptions.value = []
  }
}

function statusTagType(s: DispatchStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<DispatchStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    CANCELLED: '',
    FAILED: 'danger',
  }
  return map[s] ?? ''
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(() => {
  search()
})
</script>
