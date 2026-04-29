<template>
  <div>
    <!-- 게시판 정보 헤더 -->
    <div class="mb-4 flex items-center justify-between">
      <div>
        <h2 class="text-xl font-semibold text-gray-800">
          {{ boardMaster?.name ?? t('board.posts.title') }}
          <el-tag
            v-if="boardMaster"
            class="ml-2"
            size="small"
            type="info"
          >
            {{ t(`board.masters.type.${boardMaster.type}`) }}
          </el-tag>
        </h2>
      </div>
      <el-button
        type="primary"
        :aria-label="t('board.posts.write')"
        @click="goCreatePost"
      >
        {{ t('board.posts.write') }}
      </el-button>
    </div>

    <!-- 검색 영역 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-input
        v-model="searchQuery"
        :placeholder="t('board.posts.search')"
        clearable
        style="width: 280px"
        :aria-label="t('board.posts.search')"
        @keyup.enter="onSearch"
      >
        <template #prefix>
          <el-icon><i-ep-search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="sortOrder"
        style="width: 150px"
        :aria-label="t('board.posts.sort')"
        @change="loadPosts"
      >
        <el-option :label="t('board.posts.sortOptions.latest')" value="createdAt,desc" />
        <el-option :label="t('board.posts.sortOptions.oldest')" value="createdAt,asc" />
        <el-option :label="t('board.posts.sortOptions.viewCount')" value="viewCount,desc" />
      </el-select>

      <el-button type="primary" plain @click="onSearch">
        {{ t('board.posts.searchBtn') }}
      </el-button>
    </div>

    <!-- 검색 결과 스크린 리더 알림 — KWCAG aria-live -->
    <div aria-live="polite" aria-atomic="true" class="sr-only">{{ liveAnnouncement }}</div>

    <!-- 게시글 테이블 -->
    <el-table
      v-loading="loading"
      :data="posts"
      stripe
      :empty-text="t('board.posts.empty')"
      :aria-label="t('board.posts.title')"
      class="w-full cursor-pointer"
      @row-click="goDetail"
    >
      <caption class="sr-only">{{ boardMaster?.name ?? t('board.posts.title') }}</caption>

      <el-table-column
        type="index"
        :index="rowIndex"
        :label="t('board.posts.field.no')"
        width="70"
        align="center"
      />
      <el-table-column
        prop="title"
        :label="t('board.posts.field.title')"
        min-width="260"
      >
        <template #default="{ row }">
          <div class="flex items-center gap-1">
            <!-- 공지 핀 아이콘 — KWCAG: 텍스트 대체 제공 -->
            <el-tag
              v-if="row.isNotice"
              size="small"
              type="danger"
              :aria-label="t('board.posts.notice')"
            >
              {{ t('board.posts.notice') }}
            </el-tag>
            <span class="truncate">{{ row.title }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        prop="authorUsername"
        :label="t('board.posts.field.author')"
        width="120"
      />
      <el-table-column
        prop="viewCount"
        :label="t('board.posts.field.viewCount')"
        width="80"
        align="right"
      >
        <template #default="{ row }">
          <span class="text-sm text-gray-500">{{ row.viewCount.toLocaleString() }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('board.posts.field.createdAt')"
        width="140"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
    </el-table>

    <!-- 빈 상태 -->
    <el-empty
      v-if="!loading && posts.length === 0"
      :description="t('board.posts.empty')"
      :image-size="120"
      class="mt-8"
    />

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="totalElements"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50]"
        :aria-label="t('a11y.pagination')"
        @change="loadPosts"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { boardApi } from '@/api/board'
import type { PostSummary, BbsMasterDetail } from '@iroum/shared/types/api'

interface Props {
  bbsId: string
}
const props = defineProps<Props>()

const { t } = useI18n()
const router = useRouter()

const posts = ref<PostSummary[]>([])
const boardMaster = ref<BbsMasterDetail | null>(null)
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchQuery = ref('')
const sortOrder = ref('createdAt,desc')
const liveAnnouncement = ref('')

// @MX:ANCHOR: [AUTO] loadPosts — onMounted, 페이지네이션, 검색 변경 시 호출
// @MX:REASON: fan_in >= 3: 마운트, 페이지 변경, 검색 버튼, 정렬 변경에서 공통 호출
async function loadPosts(): Promise<void> {
  loading.value = true
  try {
    const res = await boardApi.listPosts({
      bbsId: Number(props.bbsId),
      page: currentPage.value - 1,
      size: pageSize.value,
      search: searchQuery.value || undefined,
      sort: sortOrder.value,
    })
    posts.value = res.data.content
    totalElements.value = res.data.totalElements
    liveAnnouncement.value = t('board.posts.resultCount', { count: res.data.totalElements })
  } catch {
    ElMessage.error(t('board.posts.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadBoardMaster(): Promise<void> {
  try {
    const res = await boardApi.getMaster(Number(props.bbsId))
    boardMaster.value = res.data
  } catch {
    // 게시판 마스터 로드 실패 시 타이틀만 fallback
  }
}

function onSearch(): void {
  currentPage.value = 1
  loadPosts()
}

function goDetail(row: PostSummary): void {
  router.push({ name: 'board-post-detail', params: { id: row.id } })
}

function goCreatePost(): void {
  router.push({ name: 'board-post-create', params: { bbsId: props.bbsId } })
}

function rowIndex(index: number): number {
  return totalElements.value - (currentPage.value - 1) * pageSize.value - index
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  })
}

onMounted(() => {
  loadBoardMaster()
  loadPosts()
})
</script>
