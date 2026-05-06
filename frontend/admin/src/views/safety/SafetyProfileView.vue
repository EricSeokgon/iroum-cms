<template>
  <!-- 안전 프로필 — SPEC-CMS-006 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">기업 안전 프로필</h2>
      <el-button type="success" :disabled="!hasProfile" @click="goMatch">매칭 실행</el-button>
    </div>

    <el-card v-loading="store.profileLoading" shadow="never" class="max-w-3xl">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="업종 코드" prop="industry_code">
          <el-input v-model="form.industry_code" placeholder="예: 건설업, 제조업" />
        </el-form-item>

        <el-form-item label="공정 유형">
          <el-input v-model="form.process_type" placeholder="예: 고소작업, 용접, 화학공정" />
        </el-form-item>

        <el-form-item label="위험요소" prop="hazard_factors">
          <div class="w-full">
            <el-tag
              v-for="(h, i) in form.hazard_factors"
              :key="i"
              closable
              class="mr-2 mb-1"
              type="warning"
              @close="removeHazard(i)"
            >
              {{ h }}
            </el-tag>
            <el-input
              v-if="hazardEditing"
              v-model="hazardNew"
              size="small"
              style="width: 200px"
              placeholder="위험요소 추가"
              @keyup.enter="addHazard"
              @blur="addHazard"
            />
            <el-button v-else size="small" @click="hazardEditing = true">+ 추가</el-button>
          </div>
        </el-form-item>

        <el-form-item label="직원 수">
          <el-input-number v-model="form.employee_count" :min="0" :max="999999" />
        </el-form-item>

        <el-form-item label="리스크 등급" prop="risk_grade">
          <el-select v-model="form.risk_grade" placeholder="등급 선택">
            <el-option label="A (매우 낮음)" value="A" />
            <el-option label="B (낮음)" value="B" />
            <el-option label="C (보통)" value="C" />
            <el-option label="D (높음)" value="D" />
            <el-option label="E (매우 높음)" value="E" />
          </el-select>
        </el-form-item>

        <el-form-item label="비고">
          <el-input v-model="form.notes" type="textarea" :rows="3" />
        </el-form-item>

        <el-form-item v-if="store.myProfile?.updated_at">
          <p class="text-xs text-gray-500">
            최근 업데이트: {{ formatDate(store.myProfile.updated_at) }}
          </p>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">저장</el-button>
          <el-button @click="resetForm">되돌리기</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useSafetyStore } from '@/stores/safetyStore'
import type { SafetyProfileUpsertRequest, RiskGrade } from '@/api/safety'

const router = useRouter()
const store = useSafetyStore()

const formRef = ref<FormInstance>()
const saving = ref(false)
const hazardEditing = ref(false)
const hazardNew = ref('')

const form = reactive<SafetyProfileUpsertRequest>({
  industry_code: '',
  process_type: '',
  hazard_factors: [],
  employee_count: 0,
  risk_grade: 'C' as RiskGrade,
  notes: '',
})

const rules: FormRules = {
  industry_code: [{ required: true, message: '업종 코드는 필수입니다', trigger: 'blur' }],
  risk_grade: [{ required: true, message: '리스크 등급은 필수입니다', trigger: 'change' }],
}

const hasProfile = computed(() => !!store.myProfile)

function loadFromStore(): void {
  if (!store.myProfile) return
  Object.assign(form, {
    industry_code: store.myProfile.industry_code,
    process_type: store.myProfile.process_type ?? '',
    hazard_factors: [...(store.myProfile.hazard_factors ?? [])],
    employee_count: store.myProfile.employee_count ?? 0,
    risk_grade: store.myProfile.risk_grade,
    notes: store.myProfile.notes ?? '',
  })
}

function addHazard(): void {
  const v = hazardNew.value.trim()
  if (v && !form.hazard_factors.includes(v)) {
    form.hazard_factors.push(v)
  }
  hazardNew.value = ''
  hazardEditing.value = false
}

function removeHazard(idx: number): void {
  form.hazard_factors.splice(idx, 1)
}

function resetForm(): void {
  loadFromStore()
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await store.upsertProfile(form)
      ElMessage.success('안전 프로필이 저장되었습니다')
    } catch {
      ElMessage.error('저장 실패')
    } finally {
      saving.value = false
    }
  })
}

function goMatch(): void {
  router.push({ name: 'safety-match' })
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(async () => {
  await store.fetchMyProfile()
  loadFromStore()
})
</script>
