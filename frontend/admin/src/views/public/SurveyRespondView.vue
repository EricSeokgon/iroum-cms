<template>
  <!-- 설문 공개 응답 페이지 — 인증 불필요, /public/survey/:id -->
  <div class="min-h-screen bg-gray-50 py-10 px-4">
    <div class="mx-auto max-w-2xl">

      <!-- 로딩 -->
      <div v-if="loading" class="flex justify-center py-20">
        <el-icon class="animate-spin text-4xl text-blue-500"><i-ep-loading /></el-icon>
      </div>

      <!-- 오류: 조회 실패 / 비공개 -->
      <el-result
        v-else-if="errorState"
        :icon="errorState === 'not-open' ? 'warning' : 'error'"
        :title="errorState === 'not-open' ? '참여할 수 없는 설문입니다' : '설문을 불러오지 못했습니다'"
        :sub-title="errorState === 'not-open' ? '설문이 아직 시작되지 않았거나 이미 마감되었습니다.' : '잠시 후 다시 시도해주세요.'"
      />

      <!-- 제출 완료 -->
      <el-result
        v-else-if="submitted"
        icon="success"
        title="응답이 제출되었습니다"
        sub-title="소중한 의견 감사합니다."
      >
        <template #extra>
          <el-button @click="() => window.close()">창 닫기</el-button>
        </template>
      </el-result>

      <!-- 설문 본문 -->
      <template v-else-if="survey">
        <!-- 헤더 -->
        <div class="mb-6 rounded-lg bg-white p-6 shadow-sm">
          <h1 class="text-2xl font-bold text-gray-900">{{ survey.title }}</h1>
          <div
            v-if="survey.descriptionHtml"
            class="mt-3 text-sm text-gray-600 prose prose-sm max-w-none"
            v-html="survey.descriptionHtml"
          />
          <div class="mt-4 flex flex-wrap gap-4 text-xs text-gray-400">
            <span>기간: {{ formatDate(survey.startAt) }} ~ {{ formatDate(survey.endAt) }}</span>
            <span v-if="survey.maxResponses">
              응답 {{ survey.responseCount.toLocaleString() }} / {{ survey.maxResponses.toLocaleString() }}
            </span>
            <span v-if="survey.isAnonymous">익명 설문</span>
          </div>
        </div>

        <!-- 문항 -->
        <div class="space-y-4">
          <div
            v-for="(q, idx) in survey.questions"
            :key="q.id"
            class="rounded-lg bg-white p-6 shadow-sm"
          >
            <p class="mb-3 font-medium text-gray-800">
              <span class="mr-1 text-gray-400">{{ idx + 1 }}.</span>
              {{ q.questionText }}
              <span v-if="q.required" class="ml-1 text-red-500">*</span>
            </p>

            <!-- 단일 선택 -->
            <el-radio-group
              v-if="q.questionType === 'SINGLE'"
              v-model="answers[q.id].answerOptions"
              class="flex flex-col gap-2"
            >
              <el-radio
                v-for="opt in parseOptions(q.options)"
                :key="opt.value"
                :value="JSON.stringify([opt.value])"
              >
                {{ opt.label }}
              </el-radio>
            </el-radio-group>

            <!-- 복수 선택 -->
            <div v-else-if="q.questionType === 'MULTI'" class="flex flex-col gap-2">
              <el-checkbox
                v-for="opt in parseOptions(q.options)"
                :key="opt.value"
                v-model="multiChecked[q.id]"
                :value="opt.value"
                @change="syncMulti(q.id)"
              >
                {{ opt.label }}
              </el-checkbox>
            </div>

            <!-- 주관식 텍스트 -->
            <el-input
              v-else-if="q.questionType === 'TEXT'"
              v-model="answers[q.id].answerText"
              type="textarea"
              :rows="3"
              placeholder="의견을 입력해주세요"
            />

            <!-- 평점 -->
            <div v-else-if="q.questionType === 'RATING'" class="flex items-center gap-2">
              <el-rate
                v-model="answers[q.id].answerRating"
                :max="5"
                show-score
                score-template="{value}점"
              />
            </div>

            <!-- 날짜 -->
            <el-date-picker
              v-else-if="q.questionType === 'DATE'"
              v-model="answers[q.id].answerDate"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="날짜 선택"
            />
          </div>
        </div>

        <!-- 제출 -->
        <div class="mt-6 flex justify-end">
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            @click="submit"
          >
            제출하기
          </el-button>
        </div>
      </template>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSurvey, submitSurveyResponse } from '@/api/survey'
import type { SurveyDetail, SurveyAnswerRequest } from '@/api/survey'

const route = useRoute()
const surveyId = Number(route.params.id)

const survey = ref<SurveyDetail | null>(null)
const loading = ref(true)
const errorState = ref<'not-open' | 'error' | null>(null)
const submitted = ref(false)
const submitting = ref(false)

// answers[questionId] — 각 문항 응답값 저장
const answers = reactive<Record<number, SurveyAnswerRequest>>({})
// MULTI 체크박스 바인딩용 (string[] per questionId)
const multiChecked = reactive<Record<number, string[]>>({})

function parseOptions(raw: string | null): Array<{ value: string; label: string }> {
  if (!raw) return []
  try { return JSON.parse(raw) } catch { return [] }
}

function syncMulti(qId: number): void {
  answers[qId].answerOptions = JSON.stringify(multiChecked[qId] ?? [])
}

function formatDate(iso: string): string {
  return iso?.slice(0, 10) ?? ''
}

async function load(): Promise<void> {
  try {
    const res = await getSurvey(surveyId)
    const s = res.data
    if (s.status !== 'OPEN') {
      errorState.value = 'not-open'
      return
    }
    survey.value = s
    // 응답 초기화
    for (const q of s.questions) {
      answers[q.id] = { questionId: q.id }
      if (q.questionType === 'MULTI') multiChecked[q.id] = []
    }
  } catch {
    errorState.value = 'error'
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!survey.value) return

  // 필수 항목 검증
  for (const q of survey.value.questions) {
    if (!q.required) continue
    const a = answers[q.id]
    const empty =
      (q.questionType === 'TEXT' && !a.answerText?.trim()) ||
      (['SINGLE', 'MULTI'].includes(q.questionType) && !a.answerOptions) ||
      (q.questionType === 'RATING' && (a.answerRating == null || a.answerRating === 0)) ||
      (q.questionType === 'DATE' && !a.answerDate)
    if (empty) {
      ElMessage.warning(`${survey.value.questions.indexOf(q) + 1}번 문항은 필수 응답입니다.`)
      return
    }
  }

  submitting.value = true
  try {
    await submitSurveyResponse(surveyId, { answers: Object.values(answers) })
    submitted.value = true
  } catch {
    ElMessage.error('제출에 실패했습니다. 잠시 후 다시 시도해주세요.')
  } finally {
    submitting.value = false
  }
}

// window 참조 (창 닫기 버튼용)
const window = globalThis.window

onMounted(load)
</script>
