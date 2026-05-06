<template>
  <!-- 사고사례 상세 — SPEC-CMS-006 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ isNewMode ? '사고사례 등록' : '사고사례 상세' }}
      </h2>
      <div class="flex gap-2">
        <el-button @click="goList">목록</el-button>
        <template v-if="!isNewMode && isAdmin && !editMode">
          <el-button type="primary" @click="enterEdit">수정</el-button>
          <el-button type="danger" @click="handleDelete">삭제</el-button>
        </template>
      </div>
    </div>

    <el-card v-loading="store.incidentLoading" shadow="never">
      <!-- 보기 모드 -->
      <template v-if="!isNewMode && !editMode && store.currentIncident">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="사고 유형">
            {{ store.currentIncident.incident_type }}
          </el-descriptions-item>
          <el-descriptions-item label="업종 코드">
            {{ store.currentIncident.industry_code }}
          </el-descriptions-item>
          <el-descriptions-item label="중증도">
            <el-tag :type="severityType(store.currentIncident.severity)" size="small">
              {{ store.currentIncident.severity }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="발생일">
            {{ formatDate(store.currentIncident.occurred_at) }}
          </el-descriptions-item>
          <el-descriptions-item label="공정 유형">
            {{ store.currentIncident.process_type ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="출처">
            {{ store.currentIncident.source ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="요약" :span="2">
            {{ store.currentIncident.summary }}
          </el-descriptions-item>
          <el-descriptions-item label="상세 설명" :span="2">
            <pre class="whitespace-pre-wrap text-sm">{{ store.currentIncident.description ?? '-' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="원인" :span="2">
            <pre class="whitespace-pre-wrap text-sm">{{ store.currentIncident.cause ?? '-' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="대응방안" :span="2">
            <pre class="whitespace-pre-wrap text-sm">{{ store.currentIncident.countermeasure ?? '-' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="위험요소" :span="2">
            <el-tag
              v-for="h in store.currentIncident.hazard_factors ?? []"
              :key="h"
              size="small"
              type="warning"
              class="mr-1"
            >
              {{ h }}
            </el-tag>
            <span v-if="!store.currentIncident.hazard_factors?.length">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="키워드" :span="2">
            <el-tag
              v-for="k in store.currentIncident.keywords ?? []"
              :key="k"
              size="small"
              class="mr-1"
            >
              {{ k }}
            </el-tag>
            <span v-if="!store.currentIncident.keywords?.length">-</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>

      <!-- 등록/수정 모드 -->
      <el-form
        v-if="isNewMode || editMode"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="110px"
        class="max-w-4xl"
      >
        <el-form-item label="사고 유형" prop="incident_type">
          <el-input v-model="form.incident_type" placeholder="예: 추락, 화재, 폭발" />
        </el-form-item>
        <el-form-item label="업종 코드" prop="industry_code">
          <el-input v-model="form.industry_code" placeholder="예: 건설업, 제조업" />
        </el-form-item>
        <el-form-item label="중증도" prop="severity">
          <el-select v-model="form.severity" placeholder="선택">
            <el-option label="LOW" value="LOW" />
            <el-option label="MEDIUM" value="MEDIUM" />
            <el-option label="HIGH" value="HIGH" />
            <el-option label="CRITICAL" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="발생일" prop="occurred_at">
          <el-date-picker
            v-model="form.occurred_at"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="공정 유형">
          <el-input v-model="form.process_type" />
        </el-form-item>
        <el-form-item label="요약" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="상세 설명">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="원인">
          <el-input v-model="form.cause" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="대응방안">
          <el-input v-model="form.countermeasure" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="위험요소(쉼표)">
          <el-input v-model="hazardInput" placeholder="감전, 추락, 끼임" />
        </el-form-item>
        <el-form-item label="키워드(쉼표)">
          <el-input v-model="keywordInput" placeholder="고소작업, 안전대" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useSafetyStore } from '@/stores/safetyStore'
import { useAuthStore } from '@/stores/auth'
import type { IncidentSeverity, IncidentCreateRequest } from '@/api/safety'

const route = useRoute()
const router = useRouter()
const store = useSafetyStore()
const auth = useAuthStore()

const isNewMode = computed(() => route.params.id === 'new')
const incidentId = computed(() => Number(route.params.id))

const isAdmin = computed(() =>
  (auth.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

const editMode = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const hazardInput = ref('')
const keywordInput = ref('')

const form = reactive<IncidentCreateRequest>({
  incident_type: '',
  industry_code: '',
  severity: 'MEDIUM' as IncidentSeverity,
  occurred_at: new Date().toISOString().slice(0, 10),
  summary: '',
  description: '',
  cause: '',
  countermeasure: '',
  process_type: '',
  hazard_factors: [],
  keywords: [],
})

const rules: FormRules = {
  incident_type: [{ required: true, message: '사고 유형은 필수입니다', trigger: 'blur' }],
  industry_code: [{ required: true, message: '업종 코드는 필수입니다', trigger: 'blur' }],
  severity: [{ required: true, message: '중증도는 필수입니다', trigger: 'change' }],
  occurred_at: [{ required: true, message: '발생일은 필수입니다', trigger: 'change' }],
  summary: [{ required: true, message: '요약은 필수입니다', trigger: 'blur' }],
}

function severityType(s: IncidentSeverity): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<IncidentSeverity, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    LOW: 'info',
    MEDIUM: '',
    HIGH: 'warning',
    CRITICAL: 'danger',
  }
  return map[s] ?? ''
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR')
}

function goList(): void {
  router.push({ name: 'safety-incidents' })
}

function enterEdit(): void {
  if (!store.currentIncident) return
  Object.assign(form, {
    incident_type: store.currentIncident.incident_type,
    industry_code: store.currentIncident.industry_code,
    severity: store.currentIncident.severity,
    occurred_at: store.currentIncident.occurred_at?.slice(0, 10) ?? '',
    summary: store.currentIncident.summary,
    description: store.currentIncident.description ?? '',
    cause: store.currentIncident.cause ?? '',
    countermeasure: store.currentIncident.countermeasure ?? '',
    process_type: store.currentIncident.process_type ?? '',
    hazard_factors: store.currentIncident.hazard_factors ?? [],
    keywords: store.currentIncident.keywords ?? [],
  })
  hazardInput.value = (form.hazard_factors ?? []).join(', ')
  keywordInput.value = (form.keywords ?? []).join(', ')
  editMode.value = true
}

function cancelEdit(): void {
  editMode.value = false
  if (isNewMode.value) goList()
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      form.hazard_factors = hazardInput.value.split(',').map(s => s.trim()).filter(Boolean)
      form.keywords = keywordInput.value.split(',').map(s => s.trim()).filter(Boolean)
      if (isNewMode.value) {
        const created = await store.createIncident(form)
        ElMessage.success('사고사례 등록 완료')
        router.replace({ name: 'safety-incident-detail', params: { id: created.id } })
      } else {
        await store.updateIncident(incidentId.value, form)
        ElMessage.success('수정 완료')
        editMode.value = false
        await store.fetchIncident(incidentId.value)
      }
    } catch {
      ElMessage.error('저장 실패')
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(): Promise<void> {
  try {
    await ElMessageBox.confirm('이 사고사례를 삭제하시겠습니까?', '삭제 확인', {
      type: 'warning',
      confirmButtonText: '삭제',
      cancelButtonText: '취소',
    })
    await store.deleteIncident(incidentId.value)
    ElMessage.success('삭제되었습니다')
    goList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('삭제 실패')
  }
}

onMounted(() => {
  if (!isNewMode.value) {
    store.fetchIncident(incidentId.value)
  } else {
    editMode.value = true
  }
})
</script>
