<template>
  <!-- @MX:NOTE: [AUTO] 재사용 컴포넌트 — admin PostFormView, public QnaCreateView 공통 사용 (SPEC-CMS-AI-004) -->
  <div class="tag-recommendation-input">
    <!-- 현재 선택된 태그 + 자유 입력 -->
    <div class="flex flex-wrap items-center gap-1">
      <el-tag
        v-for="tag in modelValue"
        :key="tag"
        closable
        :aria-label="`${tag} 태그 삭제`"
        @close="removeTag(tag)"
      >{{ tag }}</el-tag>
      <el-input
        v-model="inputValue"
        size="small"
        style="width: 160px"
        :placeholder="t('board.posts.field.tagPlaceholder')"
        :aria-label="t('board.posts.field.tagPlaceholder')"
        @keyup.enter="addTag"
        @keydown="onKeydown"
      />
    </div>

    <!-- AI 추천 태그 -->
    <div v-if="filteredRecommendations.length > 0" class="mt-2 flex flex-wrap items-center gap-1">
      <span class="mr-1 text-xs text-gray-500">{{ t('board.posts.field.aiRecommend') }}</span>
      <el-tag
        v-for="tag in filteredRecommendations"
        :key="tag"
        type="info"
        class="cursor-pointer"
        :aria-label="`${tag} 추천 태그 추가`"
        @click="acceptRecommendation(tag)"
      >+ {{ tag }}</el-tag>
    </div>

    <!-- 추천 분석 중 표시 -->
    <div v-else-if="loading" class="mt-1 text-xs text-gray-400">
      {{ t('board.posts.field.aiAnalyzing') }}
    </div>
  </div>
</template>

<script setup lang="ts">
// 태그 입력 + AI 추천 표시 컴포넌트 (SPEC-CMS-AI-004 REQ-AI-TAG-012)
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  modelValue: string[]
  recommendations: string[]
  loading: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [tags: string[]]
  accept: [tag: string]
  reject: [tag: string]
}>()

const inputValue = ref('')

// 이미 선택된 태그는 추천 목록에서 제외
const filteredRecommendations = computed(() =>
  props.recommendations.filter((tg) => !props.modelValue.includes(tg)),
)

// 콤마 키 처리 (vue/valid-v-on에서 .comma modifier 미지원 → 직접 처리)
function onKeydown(e: KeyboardEvent): void {
  if (e.key === ',') {
    e.preventDefault()
    addTag()
  }
}

// 자유 입력 태그 추가 (Enter/콤마)
function addTag(): void {
  const tag = inputValue.value.trim().replace(/,$/, '').trim()
  if (tag && !props.modelValue.includes(tag)) {
    emit('update:modelValue', [...props.modelValue, tag])
  }
  inputValue.value = ''
}

function removeTag(tag: string): void {
  emit('update:modelValue', props.modelValue.filter((tg) => tg !== tag))
}

// 추천 태그 클릭 → 선택 목록에 추가 + 채택 피드백
function acceptRecommendation(tag: string): void {
  if (!props.modelValue.includes(tag)) {
    emit('update:modelValue', [...props.modelValue, tag])
    emit('accept', tag)
  }
}
</script>
