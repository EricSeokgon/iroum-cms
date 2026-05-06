<template>
  <!-- 정책사업 상세/수정 — SPEC-CMS-007 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ isNewMode ? '정책사업 등록' : '정책사업 상세' }}
      </h2>
      <div class="flex gap-2">
        <el-button @click="goList">목록</el-button>
        <template v-if="!isNewMode && !editMode && store.currentProgram">
          <el-button v-if="store.currentProgram.external_url" @click="handleExternalRedirect">
            외부 페이지 이동
          </el-button>
          <el-button type="success" @click="handleApplyClick">신청 클릭 추적</el-button>
          <template v-if="isAdmin">
            <el-button type="primary" @click="enterEdit">수정</el-button>
            <el-button type="danger" @click="handleDelete">삭제</el-button>
          </template>
        </template>
      </div>
    </div>

    <el-card v-loading="store.programLoading" shadow="never">
      <!-- 보기 모드 -->
      <template v-if="!isNewMode && !editMode && store.currentProgram">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="정책명" :span="2">
            {{ store.currentProgram.title }}
          </el-descriptions-item>
          <el-descriptions-item label="부처">
            {{ store.currentProgram.ministry ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="기관">
            {{ store.currentProgram.agency ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="상태">
            <el-tag :type="statusTagType(store.currentProgram.status)" size="small">
              {{ store.currentProgram.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="예산">
            <span v-if="store.currentProgram.budget_amount">
              {{ formatMoney(store.currentProgram.budget_amount) }}
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="대상 업종" :span="2">
            <el-tag
              v-for="i in store.currentProgram.target_industries ?? []"
              :key="i"
              size="small"
              class="mr-1"
            >
              {{ i }}
            </el-tag>
            <span v-if="!store.currentProgram.target_industries?.length">전체</span>
          </el-descriptions-item>
          <el-descriptions-item label="대상 지역" :span="2">
            <el-tag
              v-for="r in store.currentProgram.target_regions ?? []"
              :key="r"
              size="small"
              type="info"
              class="mr-1"
            >
              {{ r }}
            </el-tag>
            <span v-if="!store.currentProgram.target_regions?.length">전국</span>
          </el-descriptions-item>
          <el-descriptions-item label="직원수 범위">
            {{ rangeText(store.currentProgram.employee_count_min, store.currentProgram.employee_count_max, '명') }}
          </el-descriptions-item>
          <el-descriptions-item label="업력 범위">
            {{ rangeText(store.currentProgram.business_age_min, store.currentProgram.business_age_max, '년') }}
          </el-descriptions-item>
          <el-descriptions-item label="매출 범위" :span="2">
            <span v-if="store.currentProgram.revenue_min || store.currentProgram.revenue_max">
              {{ formatMoney(store.currentProgram.revenue_min ?? 0) }} ~ {{ formatMoney(store.currentProgram.revenue_max ?? 0) }}
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="신청 시작">
            {{ store.currentProgram.application_start_at ? formatDate(store.currentProgram.application_start_at) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="신청 마감">
            {{ store.currentProgram.application_end_at ? formatDate(store.currentProgram.application_end_at) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="필수 인증" :span="2">
            <el-tag
              v-for="c in store.currentProgram.required_certifications ?? []"
              :key="c"
              size="small"
              type="warning"
              class="mr-1"
            >
              {{ c }}
            </el-tag>
            <span v-if="!store.currentProgram.required_certifications?.length">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="제외 업종" :span="2">
            <el-tag
              v-for="x in store.currentProgram.excluded_industries ?? []"
              :key="x"
              size="small"
              type="danger"
              class="mr-1"
            >
              {{ x }}
            </el-tag>
            <span v-if="!store.currentProgram.excluded_industries?.length">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="신청 방법" :span="2">
            <pre class="whitespace-pre-wrap text-sm">{{ store.currentProgram.application_method ?? '-' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="설명" :span="2">
            <pre class="whitespace-pre-wrap text-sm">{{ store.currentProgram.description ?? '-' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="문의처" :span="2">
            <div class="text-sm">
              <p v-if="store.currentProgram.contact_dept">부서: {{ store.currentProgram.contact_dept }}</p>
              <p v-if="store.currentProgram.contact_phone">전화: {{ store.currentProgram.contact_phone }}</p>
              <p v-if="store.currentProgram.contact_email">이메일: {{ store.currentProgram.contact_email }}</p>
              <p
                v-if="!store.currentProgram.contact_dept && !store.currentProgram.contact_phone && !store.currentProgram.contact_email"
              >
                -
              </p>
            </div>
          </el-descriptions-item>
        </el-descriptions>
      </template>

      <!-- 등록/수정 모드 -->
      <el-form
        v-if="isNewMode || editMode"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        class="max-w-4xl"
      >
        <el-form-item label="정책명" prop="title">
          <el-input v-model="form.title" placeholder="정책사업명" />
        </el-form-item>
        <el-form-item label="부처">
          <el-input v-model="form.ministry" placeholder="예: 중소벤처기업부" />
        </el-form-item>
        <el-form-item label="기관">
          <el-input v-model="form.agency" placeholder="예: 창업진흥원" />
        </el-form-item>
        <el-form-item label="상태" prop="status">
          <el-select v-model="form.status" placeholder="선택">
            <el-option label="DRAFT" value="DRAFT" />
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="CLOSED" value="CLOSED" />
            <el-option label="EXPIRED" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="외부 URL">
          <el-input v-model="form.external_url" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="대상 업종">
          <el-input
            v-model="industriesText"
            placeholder="콤마(,)로 구분 — 예: 제조업, 정보통신업"
          />
        </el-form-item>
        <el-form-item label="대상 지역">
          <el-input
            v-model="regionsText"
            placeholder="콤마(,)로 구분 — 예: 서울, 경기"
          />
        </el-form-item>
        <el-form-item label="직원수 (최소~최대)">
          <div class="flex items-center gap-2">
            <el-input-number v-model="form.employee_count_min" :min="0" placeholder="최소" />
            <span>~</span>
            <el-input-number v-model="form.employee_count_max" :min="0" placeholder="최대" />
            <span class="text-sm text-gray-500">명</span>
          </div>
        </el-form-item>
        <el-form-item label="업력 (최소~최대)">
          <div class="flex items-center gap-2">
            <el-input-number v-model="form.business_age_min" :min="0" placeholder="최소" />
            <span>~</span>
            <el-input-number v-model="form.business_age_max" :min="0" placeholder="최대" />
            <span class="text-sm text-gray-500">년</span>
          </div>
        </el-form-item>
        <el-form-item label="매출 (최소~최대)">
          <div class="flex items-center gap-2">
            <el-input-number v-model="form.revenue_min" :min="0" :step="1000000" placeholder="최소" />
            <span>~</span>
            <el-input-number v-model="form.revenue_max" :min="0" :step="1000000" placeholder="최대" />
            <span class="text-sm text-gray-500">원</span>
          </div>
        </el-form-item>
        <el-form-item label="신청 시작">
          <el-date-picker
            v-model="form.application_start_at"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="시작 시각"
          />
        </el-form-item>
        <el-form-item label="신청 마감">
          <el-date-picker
            v-model="form.application_end_at"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="마감 시각"
          />
        </el-form-item>
        <el-form-item label="예산 (원)">
          <el-input-number v-model="form.budget_amount" :min="0" :step="1000000" />
        </el-form-item>
        <el-form-item label="필수 인증">
          <el-input
            v-model="certificationsText"
            placeholder="콤마(,)로 구분 — 예: 벤처기업, INNO-BIZ"
          />
        </el-form-item>
        <el-form-item label="설명">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="신청 방법">
          <el-input v-model="form.application_method" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="문의 부서">
          <el-input v-model="form.contact_dept" />
        </el-form-item>
        <el-form-item label="문의 전화">
          <el-input v-model="form.contact_phone" />
        </el-form-item>
        <el-form-item label="문의 이메일">
          <el-input v-model="form.contact_email" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">저장</el-button>
          <el-button @click="cancelEdit">취소</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { usePolicyStore } from '@/stores/policyStore'
import { useAuthStore } from '@/stores/auth'
import type { PolicyProgramRequest, PolicyStatus } from '@/api/policy'

const router = useRouter()
const route = useRoute()
const store = usePolicyStore()
const auth = useAuthStore()

const props = defineProps<{ id: string | number }>()

const isAdmin = computed(() =>
  (auth.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

const isNewMode = computed(() => props.id === 'new' || route.params.id === 'new')
const editMode = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<PolicyProgramRequest>({
  title: '',
  ministry: undefined,
  agency: undefined,
  status: 'DRAFT',
  description: undefined,
  external_url: undefined,
  target_industries: undefined,
  target_regions: undefined,
  application_start_at: undefined,
  application_end_at: undefined,
  budget_amount: undefined,
  employee_count_min: undefined,
  employee_count_max: undefined,
  revenue_min: undefined,
  revenue_max: undefined,
  business_age_min: undefined,
  business_age_max: undefined,
  required_certifications: undefined,
  application_method: undefined,
  contact_dept: undefined,
  contact_phone: undefined,
  contact_email: undefined,
})

// 콤마 입력 보조 — 배열 ↔ 문자열 변환
const industriesText = ref('')
const regionsText = ref('')
const certificationsText = ref('')

watch(industriesText, v => {
  form.target_industries = v ? v.split(',').map(s => s.trim()).filter(Boolean) : undefined
})
watch(regionsText, v => {
  form.target_regions = v ? v.split(',').map(s => s.trim()).filter(Boolean) : undefined
})
watch(certificationsText, v => {
  form.required_certifications = v ? v.split(',').map(s => s.trim()).filter(Boolean) : undefined
})

const rules: FormRules = {
  title: [{ required: true, message: '정책명을 입력하세요', trigger: 'blur' }],
  status: [{ required: true, message: '상태를 선택하세요', trigger: 'change' }],
}

function enterEdit(): void {
  if (!store.currentProgram) return
  // currentProgram → form
  Object.assign(form, {
    title: store.currentProgram.title,
    ministry: store.currentProgram.ministry,
    agency: store.currentProgram.agency,
    status: store.currentProgram.status,
    description: store.currentProgram.description,
    external_url: store.currentProgram.external_url,
    target_industries: store.currentProgram.target_industries,
    target_regions: store.currentProgram.target_regions,
    application_start_at: store.currentProgram.application_start_at,
    application_end_at: store.currentProgram.application_end_at,
    budget_amount: store.currentProgram.budget_amount,
    employee_count_min: store.currentProgram.employee_count_min,
    employee_count_max: store.currentProgram.employee_count_max,
    revenue_min: store.currentProgram.revenue_min,
    revenue_max: store.currentProgram.revenue_max,
    business_age_min: store.currentProgram.business_age_min,
    business_age_max: store.currentProgram.business_age_max,
    required_certifications: store.currentProgram.required_certifications,
    application_method: store.currentProgram.application_method,
    contact_dept: store.currentProgram.contact_dept,
    contact_phone: store.currentProgram.contact_phone,
    contact_email: store.currentProgram.contact_email,
  })
  industriesText.value = (store.currentProgram.target_industries ?? []).join(', ')
  regionsText.value = (store.currentProgram.target_regions ?? []).join(', ')
  certificationsText.value = (store.currentProgram.required_certifications ?? []).join(', ')
  editMode.value = true
}

function cancelEdit(): void {
  editMode.value = false
  if (isNewMode.value) goList()
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isNewMode.value) {
      const created = await store.createProgram(form)
      ElMessage.success('정책사업이 등록되었습니다')
      router.replace({ name: 'policy-program-detail', params: { id: created.id } })
    } else {
      await store.updateProgram(Number(props.id), form)
      ElMessage.success('정책사업이 수정되었습니다')
      editMode.value = false
      await store.fetchProgram(Number(props.id))
    }
  } catch {
    ElMessage.error('저장 실패')
  } finally {
    saving.value = false
  }
}

async function handleDelete(): Promise<void> {
  try {
    await ElMessageBox.confirm('정말로 이 정책사업을 삭제하시겠습니까?', '삭제 확인', {
      confirmButtonText: '삭제',
      cancelButtonText: '취소',
      type: 'warning',
    })
    await store.deleteProgram(Number(props.id))
    ElMessage.success('삭제되었습니다')
    goList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('삭제 실패')
  }
}

async function handleApplyClick(): Promise<void> {
  try {
    await store.trackEvent(Number(props.id), 'CLICK_APPLY')
    ElMessage.success('신청 클릭 이벤트가 기록되었습니다')
  } catch {
    ElMessage.warning('이벤트 기록 실패')
  }
}

async function handleExternalRedirect(): Promise<void> {
  if (!store.currentProgram?.external_url) return
  try {
    await store.trackEvent(Number(props.id), 'EXTERNAL_REDIRECT')
  } catch {
    // 추적 실패해도 이동은 진행
  }
  window.open(store.currentProgram.external_url, '_blank', 'noopener,noreferrer')
}

function goList(): void {
  router.push({ name: 'policy-programs' })
}

function statusTagType(s: PolicyStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<PolicyStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    DRAFT: 'info',
    ACTIVE: 'success',
    CLOSED: 'warning',
    EXPIRED: 'danger',
  }
  return map[s] ?? ''
}

function rangeText(min?: number, max?: number, unit?: string): string {
  if (min === undefined && max === undefined) return '-'
  const minStr = min !== undefined ? String(min) : '0'
  const maxStr = max !== undefined ? String(max) : '제한 없음'
  return `${minStr} ~ ${maxStr} ${unit ?? ''}`
}

function formatMoney(n: number): string {
  return `${n.toLocaleString('ko-KR')} 원`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(async () => {
  if (isNewMode.value) {
    editMode.value = true
    return
  }
  await store.fetchProgram(Number(props.id))
  // VIEW 추적
  try {
    await store.trackEvent(Number(props.id), 'VIEW')
  } catch {
    // 무시
  }
})
</script>
