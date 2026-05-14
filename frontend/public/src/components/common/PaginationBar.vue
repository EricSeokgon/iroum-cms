<!--
  SPEC-CMS-PUBLIC-001 T-006 — 공통 페이지네이션 컴포넌트
  KWCAG 2.2 AA: nav aria-label, 이전/다음 버튼 키보드 조작 가능
-->
<template>
  <nav
    v-if="totalPages > 1"
    :aria-label="t('common.pagination')"
    class="flex items-center justify-center gap-2 py-4"
  >
    <el-pagination
      :current-page="displayPage"
      :page-size="pageSize"
      :total="totalElements"
      :pager-count="5"
      layout="prev, pager, next"
      background
      :hide-on-single-page="false"
      @current-change="onPageChange"
    />
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElPagination } from 'element-plus'

const props = defineProps<{
  /** 0-indexed 현재 페이지 */
  page: number
  /** 페이지 크기 */
  pageSize: number
  /** 총 요소 개수 */
  totalElements: number
  /** 총 페이지 수 */
  totalPages: number
}>()

const emit = defineEmits<{
  /** 페이지 변경 — 0-indexed 페이지 번호를 emit */
  (e: 'change', page: number): void
}>()

const { t } = useI18n()

// el-pagination 은 1-indexed 이므로 변환
const displayPage = computed(() => props.page + 1)

function onPageChange(nextPage: number): void {
  emit('change', nextPage - 1)
}
</script>
