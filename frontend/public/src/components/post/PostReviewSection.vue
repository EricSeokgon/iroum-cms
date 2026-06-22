<!--
  SPEC-CMS-REVIEW-001 C1 — 게시물 별점 리뷰 섹션 (공개)

  - 평균 별점 + 리뷰 수 표시 (props 로 초기값 수신, 작성 후 재집계)
  - VISIBLE 리뷰 목록 표시 (비인증 포함 누구나 조회 — REQ-REV-005)
  - 인증된 사용자에게만 작성 폼 노출 (REQ-REV-001/007)
  - 본문은 plain text → 보간 자동 이스케이프, v-html 미사용 (XSS 안전)
-->
<template>
  <section class="space-y-6" aria-labelledby="post-review-heading">
    <header class="flex items-center justify-between border-b border-gray-200 pb-3">
      <h2 id="post-review-heading" class="text-lg font-bold text-content-DEFAULT">리뷰</h2>
      <div class="flex items-center gap-2" aria-label="평균 별점">
        <el-rate
          :model-value="displayAverage"
          disabled
          allow-half
          show-score
          score-template="{value}"
          text-color="#f59e0b"
        />
        <span class="text-sm text-gray-500">({{ displayCount }}개)</span>
      </div>
    </header>

    <!-- 작성 폼 (인증 사용자 전용) -->
    <form
      v-if="isAuthenticated"
      class="space-y-4 rounded-md border border-gray-200 bg-white p-4"
      aria-label="리뷰 작성"
      data-testid="review-create-form"
      @submit.prevent="onSubmit"
    >
      <div>
        <label class="mb-1 block text-sm font-medium text-content-DEFAULT">
          별점 <span class="text-red-600" aria-hidden="true">*</span>
        </label>
        <el-rate v-model="form.rating" :max="5" aria-label="별점 선택" data-testid="review-rating-input" />
        <p v-if="ratingError" class="mt-1 text-xs text-red-600" role="alert">{{ ratingError }}</p>
      </div>

      <div>
        <label for="review-content" class="mb-1 block text-sm font-medium text-content-DEFAULT">
          내용 <span class="text-gray-400">(선택)</span>
        </label>
        <el-input
          id="review-content"
          v-model="form.content"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="리뷰 내용을 입력하세요 (선택)"
          data-testid="review-content-input"
        />
      </div>

      <div class="flex justify-end">
        <el-button
          type="primary"
          native-type="submit"
          :loading="submitting"
          data-testid="review-submit"
        >
          리뷰 등록
        </el-button>
      </div>
    </form>

    <!-- 비인증 안내 -->
    <p v-else class="rounded-md bg-surface-muted px-4 py-3 text-sm text-gray-600">
      리뷰를 작성하려면 로그인이 필요합니다.
    </p>

    <!-- aria-live 알림 -->
    <div aria-live="polite" aria-atomic="true" class="sr-only">{{ liveAnnouncement }}</div>

    <!-- 리뷰 목록 -->
    <ul v-if="reviews.length" class="space-y-4" data-testid="review-list">
      <li
        v-for="review in reviews"
        :key="review.id"
        class="rounded-md border border-gray-100 bg-white p-4"
      >
        <div class="mb-2 flex items-center justify-between">
          <el-rate
            :model-value="review.rating"
            disabled
            size="small"
            text-color="#f59e0b"
          />
          <span class="text-xs text-gray-400">{{ formatDate(review.createdAt) }}</span>
        </div>
        <p v-if="review.content" class="whitespace-pre-line text-sm text-content-DEFAULT">
          {{ review.content }}
        </p>
        <p class="mt-2 text-xs text-gray-400">
          작성자 {{ review.authorId != null ? `#${review.authorId}` : '익명' }}
        </p>
      </li>
    </ul>

    <p
      v-else-if="!loading"
      class="rounded-md bg-surface-muted px-4 py-6 text-center text-sm text-gray-500"
      data-testid="review-empty"
    >
      아직 등록된 리뷰가 없습니다.
    </p>
  </section>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { reviewApi, type ReviewResponse } from '@/api/reviewApi'
import { useAuthStore } from '@/stores/authStore'

const props = defineProps<{
  postId: number
  averageRating: number
  reviewCount: number
}>()

const authStore = useAuthStore()
const isAuthenticated = computed(() => authStore.isAuthenticated)

// ── 상태 ──────────────────────────────────────────────────────────────────
const reviews = ref<ReviewResponse[]>([])
const loading = ref(false)
const submitting = ref(false)
const ratingError = ref('')
const liveAnnouncement = ref('')

// 작성 후 클라이언트에서 재집계한 값 (null 이면 props 초기값 사용)
const localAverage = ref<number | null>(null)
const localCount = ref<number | null>(null)

const displayAverage = computed(() => localAverage.value ?? props.averageRating)
const displayCount = computed(() => localCount.value ?? props.reviewCount)

const form = reactive<{ rating: number; content: string }>({
  rating: 0,
  content: '',
})

// VISIBLE 리뷰 기준으로 평균/개수 재계산 (REQ-REV-003)
function recalcAggregate(): void {
  const count = reviews.value.length
  localCount.value = count
  if (count === 0) {
    localAverage.value = 0
    return
  }
  const sum = reviews.value.reduce((acc, r) => acc + r.rating, 0)
  localAverage.value = Math.round((sum / count) * 10) / 10
}

async function loadReviews(): Promise<void> {
  loading.value = true
  try {
    reviews.value = await reviewApi.list(props.postId)
    liveAnnouncement.value = `리뷰 ${reviews.value.length}건을 불러왔습니다`
  } catch {
    ElMessage.error('리뷰를 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

async function onSubmit(): Promise<void> {
  if (form.rating < 1 || form.rating > 5) {
    ratingError.value = '별점을 선택해주세요 (1~5점)'
    return
  }
  ratingError.value = ''

  submitting.value = true
  try {
    await reviewApi.create(props.postId, {
      rating: form.rating,
      content: form.content.trim() ? form.content.trim() : null,
    })
    ElMessage.success('리뷰가 등록되었습니다')
    form.rating = 0
    form.content = ''
    await loadReviews()
    recalcAggregate()
  } catch {
    ElMessage.error('리뷰 등록에 실패했습니다')
  } finally {
    submitting.value = false
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

onMounted(loadReviews)
</script>
