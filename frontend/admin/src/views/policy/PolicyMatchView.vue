<template>
  <!-- 정책 매칭 — SPEC-CMS-007 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">정책 매칭</h2>
      <div class="flex gap-2">
        <el-tag size="small" type="info">캐시 TTL 7일</el-tag>
        <el-button type="primary" :loading="store.matchLoading" @click="runMatch">
          매칭 실행
        </el-button>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <!-- 좌측: 기업 프로필 -->
      <el-card shadow="never" v-loading="store.profileLoading">
        <template #header>
          <span class="font-medium">기업 프로필</span>
        </template>
        <el-form ref="formRef" :model="profile" :rules="rules" label-width="110px">
          <el-form-item label="업종" prop="industry_code">
            <el-input v-model="profile.industry_code" placeholder="예: 정보통신업" />
          </el-form-item>
          <el-form-item label="지역" prop="region_code">
            <el-input v-model="profile.region_code" placeholder="예: 서울" />
          </el-form-item>
          <el-form-item label="직원수" prop="employee_count">
            <el-input-number v-model="profile.employee_count" :min="0" />
            <span class="ml-2 text-sm text-gray-500">명</span>
          </el-form-item>
          <el-form-item label="연 매출" prop="annual_revenue">
            <el-input-number v-model="profile.annual_revenue" :min="0" :step="1000000" />
            <span class="ml-2 text-sm text-gray-500">원</span>
          </el-form-item>
          <el-form-item label="업력" prop="business_age_years">
            <el-input-number v-model="profile.business_age_years" :min="0" />
            <span class="ml-2 text-sm text-gray-500">년</span>
          </el-form-item>
          <el-form-item label="인증">
            <el-input
              v-model="certificationsText"
              placeholder="콤마(,)로 구분 — 예: 벤처기업, INNO-BIZ"
            />
          </el-form-item>
          <el-form-item label="키워드">
            <el-input v-model="keywordsText" placeholder="콤마(,)로 구분 — 관심 분야" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="store.profileLoading" @click="saveProfile">
              프로필 저장
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 우측: 매칭 결과 -->
      <div>
        <el-card v-if="store.matchResult" class="mb-3" shadow="never">
          <div class="flex flex-wrap items-center gap-3 text-sm">
            <el-tag size="small" :type="store.matchResult.cached ? 'info' : 'success'">
              {{ store.matchResult.cached ? '캐시 결과' : '신규 매칭' }}
            </el-tag>
            <span class="text-gray-600">생성: {{ formatDate(store.matchResult.generated_at) }}</span>
            <span class="text-gray-600">만료: {{ formatDate(store.matchResult.expires_at) }}</span>
            <span class="text-gray-600">평가 정책 수: {{ store.matchResult.total_evaluated }}</span>
          </div>
        </el-card>

        <div v-loading="store.matchLoading">
          <div
            v-if="!store.matchResult || store.matchResult.results.length === 0"
            class="py-12 text-center text-gray-500 bg-white rounded border"
          >
            매칭 결과가 없습니다. 프로필을 저장한 후 매칭을 실행하세요.
          </div>
          <div v-else class="space-y-3">
            <el-card
              v-for="m in store.matchResult.results.slice(0, 10)"
              :key="m.match_id"
              shadow="hover"
              class="cursor-pointer"
              @click="goPolicy(m.policy_id)"
            >
              <div class="flex items-start justify-between mb-2">
                <div class="min-w-0 flex-1">
                  <p class="text-xs text-gray-500">RANK #{{ m.rank }}</p>
                  <h3 class="text-base font-semibold truncate">{{ m.policy.title }}</h3>
                  <p class="text-xs text-gray-500 truncate mt-1">
                    {{ m.policy.ministry ?? '-' }}
                  </p>
                </div>
                <div class="ml-2 text-right">
                  <el-tag :type="gradeTagType(m.grade)" size="default" effect="dark">
                    {{ m.grade }}등급
                  </el-tag>
                  <p class="text-lg font-bold mt-1">{{ formatScore(m.total_score) }}</p>
                </div>
              </div>

              <!-- 점수 분해 — 차원별 바 -->
              <div class="bg-gray-50 rounded p-3 space-y-2 text-xs">
                <p class="font-medium text-gray-700 mb-1">점수 분해 (XAI)</p>
                <div v-for="dim in dimensions" :key="dim.key" class="flex items-center gap-2">
                  <span class="w-14 text-gray-600">{{ dim.label }}</span>
                  <el-progress
                    :percentage="Math.round(getDimScore(m.score_breakdown, dim.key))"
                    :stroke-width="6"
                    :color="dim.color"
                    class="flex-1"
                    :show-text="false"
                  />
                  <span class="w-12 text-right font-medium">
                    {{ Math.round(getDimScore(m.score_breakdown, dim.key)) }}
                  </span>
                </div>
                <div
                  v-if="m.score_breakdown.certification_bonus || m.score_breakdown.keyword_bonus"
                  class="pt-1 mt-1 border-t border-gray-200 flex gap-2 text-gray-500"
                >
                  <span v-if="m.score_breakdown.certification_bonus">
                    인증 보너스 +{{ m.score_breakdown.certification_bonus }}
                  </span>
                  <span v-if="m.score_breakdown.keyword_bonus">
                    키워드 보너스 +{{ m.score_breakdown.keyword_bonus }}
                  </span>
                </div>
              </div>
            </el-card>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { usePolicyStore } from '@/stores/policyStore'
import type { CompanyProfile, PolicyGrade, ScoreBreakdown } from '@/api/policy'

const router = useRouter()
const store = usePolicyStore()

const formRef = ref<FormInstance>()

const profile = reactive<CompanyProfile>({
  industry_code: '',
  region_code: '',
  employee_count: 0,
  annual_revenue: 0,
  business_age_years: 0,
  certifications: undefined,
  keywords: undefined,
})

const certificationsText = ref('')
const keywordsText = ref('')

watch(certificationsText, v => {
  profile.certifications = v ? v.split(',').map(s => s.trim()).filter(Boolean) : undefined
})
watch(keywordsText, v => {
  profile.keywords = v ? v.split(',').map(s => s.trim()).filter(Boolean) : undefined
})

const rules: FormRules = {
  industry_code: [{ required: true, message: '업종을 입력하세요', trigger: 'blur' }],
  region_code: [{ required: true, message: '지역을 입력하세요', trigger: 'blur' }],
  employee_count: [{ required: true, message: '직원수를 입력하세요', trigger: 'blur' }],
  annual_revenue: [{ required: true, message: '매출을 입력하세요', trigger: 'blur' }],
  business_age_years: [{ required: true, message: '업력을 입력하세요', trigger: 'blur' }],
}

const dimensions = [
  { key: 'industry' as const, label: '업종', color: '#409EFF' },
  { key: 'region' as const, label: '지역', color: '#67C23A' },
  { key: 'size' as const, label: '규모', color: '#E6A23C' },
  { key: 'age' as const, label: '업력', color: '#F56C6C' },
  { key: 'revenue' as const, label: '매출', color: '#909399' },
]

function getDimScore(b: ScoreBreakdown, key: keyof ScoreBreakdown): number {
  const v = b[key]
  return typeof v === 'number' ? v : 0
}

async function saveProfile(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await store.upsertCompanyProfile(profile)
    ElMessage.success('프로필이 저장되었습니다')
  } catch {
    ElMessage.error('프로필 저장 실패')
  }
}

async function runMatch(): Promise<void> {
  try {
    await store.runMatch()
    ElMessage.success('매칭이 완료되었습니다')
  } catch {
    ElMessage.error('매칭 실행 실패')
  }
}

function goPolicy(id: number): void {
  router.push({ name: 'policy-program-detail', params: { id } })
}

function gradeTagType(g: PolicyGrade): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<PolicyGrade, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    A: 'success',
    B: '',
    C: 'warning',
    D: 'danger',
  }
  return map[g] ?? ''
}

function formatScore(n: number): string {
  return `${n.toFixed(1)}`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(async () => {
  // 프로필과 캐시된 매칭 결과 동시 로드
  try {
    await store.fetchMyProfile()
    if (store.myProfile) {
      Object.assign(profile, store.myProfile)
      certificationsText.value = (store.myProfile.certifications ?? []).join(', ')
      keywordsText.value = (store.myProfile.keywords ?? []).join(', ')
    }
  } catch {
    // 신규 사용자 — 무시
  }
  try {
    await store.fetchMatchResults()
  } catch {
    // 캐시 없음 — 무시
  }
})
</script>
