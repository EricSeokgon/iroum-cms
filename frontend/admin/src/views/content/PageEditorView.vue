<template>
  <!-- 페이지 편집기 — SPEC-CMS-004 REQ-CONTENT-005-D -->
  <div v-loading="loading" class="flex gap-4">
    <!-- 좌측: SEO 메타 폼 -->
    <div class="w-72 shrink-0 space-y-4">
      <div class="rounded border border-gray-200 bg-white p-4">
        <h3 class="mb-3 font-medium text-gray-700">{{ t('content.page.editor.seo') }}</h3>
        <el-form :model="seoForm" label-position="top" size="small">
          <el-form-item>
            <template #label>
              <span class="flex items-center justify-between w-full">
                <span>{{ t('content.page.seo.title') }}</span>
                <span :class="seoForm.seoTitle.length > 60 ? 'text-red-500' : 'text-gray-400'" class="text-xs">
                  {{ seoForm.seoTitle.length }}/60
                </span>
              </span>
            </template>
            <el-input
              v-model="seoForm.seoTitle"
              :maxlength="60"
              :placeholder="t('content.page.seo.titlePlaceholder')"
              show-word-limit
            />
          </el-form-item>
          <el-form-item>
            <template #label>
              <span class="flex items-center justify-between w-full">
                <span>{{ t('content.page.seo.description') }}</span>
                <span :class="seoForm.seoDescription.length > 160 ? 'text-red-500' : 'text-gray-400'" class="text-xs">
                  {{ seoForm.seoDescription.length }}/160
                </span>
              </span>
            </template>
            <el-input
              v-model="seoForm.seoDescription"
              type="textarea"
              :rows="3"
              :maxlength="160"
              :placeholder="t('content.page.seo.descriptionPlaceholder')"
              show-word-limit
            />
          </el-form-item>
          <el-form-item :label="t('content.page.seo.keywords')">
            <el-input
              v-model="seoForm.seoKeywords"
              :placeholder="t('content.page.seo.keywordsPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('content.page.seo.ogImage')">
            <el-input v-model="seoForm.ogImageUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item :label="t('content.page.seo.canonical')">
            <el-input v-model="seoForm.canonicalUrl" placeholder="https://..." />
          </el-form-item>
          <el-button size="small" class="w-full" :loading="savingSeo" @click="saveSeo">
            {{ t('content.page.editor.saveSeo') }}
          </el-button>
        </el-form>
      </div>

      <!-- 페이지 기본 정보 -->
      <div class="rounded border border-gray-200 bg-white p-4">
        <h3 class="mb-2 font-medium text-gray-700">{{ t('content.page.editor.info') }}</h3>
        <dl class="space-y-1 text-sm">
          <div class="flex justify-between">
            <dt class="text-gray-500">{{ t('content.page.field.slug') }}</dt>
            <dd class="font-mono text-xs text-gray-700 truncate max-w-32">{{ page?.slug }}</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-gray-500">{{ t('content.page.field.version') }}</dt>
            <dd class="text-gray-700">v{{ page?.currentVersion }}</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-gray-500">{{ t('content.page.field.status') }}</dt>
            <dd>
              <el-tag v-if="page" :type="statusTagType(page.status)" size="small">
                {{ t(`content.page.status.${page.status}`) }}
              </el-tag>
            </dd>
          </div>
        </dl>
      </div>
    </div>

    <!-- 중앙: 콘텐츠 블록 편집기 -->
    <div class="flex-1 min-w-0">
      <div class="rounded border border-gray-200 bg-white p-4">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="font-medium text-gray-700">{{ t('content.page.editor.blocks') }}</h3>
          <el-button size="small" :loading="savingBlocks" type="primary" plain @click="saveBlocks">
            {{ t('content.page.editor.saveBlocks') }}
          </el-button>
        </div>
        <ContentBlockEditor
          v-if="page"
          v-model="localBlocks"
          :page-id="page.id"
        />
      </div>
    </div>

    <!-- 우측: 상태 액션 패널 -->
    <div class="w-56 shrink-0 space-y-3">
      <!-- 발행 상태 제어 -->
      <div class="rounded border border-gray-200 bg-white p-4">
        <h3 class="mb-3 font-medium text-gray-700">{{ t('content.page.editor.actions') }}</h3>
        <div class="space-y-2">
          <!-- 발행 -->
          <el-button
            v-if="page?.status === 'DRAFT' || page?.status === 'RETRACTED'"
            class="w-full"
            type="success"
            size="small"
            :loading="actioning"
            @click="publishPage"
          >
            {{ t('content.page.action.publish') }}
          </el-button>

          <!-- 예약 발행 -->
          <el-button
            v-if="page?.status === 'DRAFT' || page?.status === 'RETRACTED'"
            class="w-full"
            type="warning"
            size="small"
            plain
            @click="scheduleOpen = true"
          >
            {{ t('content.page.action.schedule') }}
          </el-button>

          <!-- 철회 -->
          <el-button
            v-if="page?.status === 'PUBLISHED'"
            class="w-full"
            type="danger"
            size="small"
            plain
            :loading="actioning"
            @click="retractPage"
          >
            {{ t('content.page.action.retract') }}
          </el-button>

          <!-- 예약 취소 -->
          <el-button
            v-if="page?.status === 'SCHEDULED'"
            class="w-full"
            type="warning"
            size="small"
            plain
            :loading="actioning"
            @click="cancelSchedule"
          >
            {{ t('content.page.action.cancelSchedule') }}
          </el-button>
        </div>
      </div>

      <!-- 이력 & 미리보기 -->
      <div class="rounded border border-gray-200 bg-white p-4 space-y-2">
        <el-button class="w-full" size="small" plain @click="historyOpen = true">
          {{ t('content.page.editor.history') }}
        </el-button>
        <el-button class="w-full" size="small" plain :loading="generatingToken" @click="generatePreview">
          {{ t('content.page.editor.preview') }}
        </el-button>
        <div v-if="previewUrl" class="mt-2">
          <a :href="previewUrl" target="_blank" class="text-xs text-blue-600 underline break-all">
            {{ t('content.page.editor.previewLink') }}
          </a>
        </div>
      </div>
    </div>
  </div>

  <!-- 예약 발행 다이얼로그 -->
  <el-dialog
    v-model="scheduleOpen"
    :title="t('content.page.scheduleDialog.title')"
    width="400px"
  >
    <el-date-picker
      v-model="scheduleAt"
      type="datetime"
      :placeholder="t('content.page.scheduleDialog.placeholder')"
      :disabled-date="disabledDate"
      class="w-full"
    />
    <template #footer>
      <el-button @click="scheduleOpen = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="actioning" @click="confirmSchedule">
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 이력 다이얼로그 -->
  <PageHistoryDialog
    v-model="historyOpen"
    :page-id="pageId"
    @rolled-back="onRolledBack"
  />

  <!-- 편집 충돌 모달 (SPEC-CMS-CONTENT-REVISION-001) -->
  <ConflictModal
    :visible="conflictVisible"
    :current-version="conflictVersion"
    @reload="reloadAfterConflict"
    @dismiss="conflictVisible = false"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import axios from 'axios'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pages } from '@/api/content'
import type { PageItemResponse as PageType, ContentBlockResponse, PageStatus } from '@/api/content'
import type { RevisionConflictPayload } from '@/types/revision'
import ContentBlockEditor from '@/components/content/ContentBlockEditor.vue'
import PageHistoryDialog from '@/components/content/PageHistoryDialog.vue'
import ConflictModal from '@/components/revision/ConflictModal.vue'

const { t } = useI18n()
const route = useRoute()

const pageId = computed(() => Number(route.params.id))

const page = ref<PageType | null>(null)
const loading = ref(false)
const savingSeo = ref(false)
const savingBlocks = ref(false)
const actioning = ref(false)
const generatingToken = ref(false)
const historyOpen = ref(false)
const scheduleOpen = ref(false)
const scheduleAt = ref<Date | null>(null)
const previewUrl = ref('')

// 낙관적 락(편집 충돌) 상태 — SPEC-CMS-CONTENT-REVISION-001
const conflictVisible = ref(false)
const conflictVersion = ref(0)

// 로컬 블록 (ContentBlockEditor와 양방향 바인딩)
const localBlocks = ref<ContentBlockResponse[]>([])

const seoForm = ref({
  seoTitle: '',
  seoDescription: '',
  seoKeywords: '',
  ogImageUrl: '',
  canonicalUrl: '',
})

function statusTagType(status: PageStatus): '' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<PageStatus, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    DRAFT: 'info',
    SCHEDULED: 'warning',
    PUBLISHED: 'success',
    RETRACTED: 'danger',
  }
  return map[status]
}

function disabledDate(date: Date): boolean {
  return date < new Date()
}

onMounted(loadPage)

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const [pageRes, blockRes] = await Promise.all([
      pages.get(pageId.value),
      pages.listBlocks(pageId.value),
    ])
    page.value = pageRes.data
    localBlocks.value = Array.isArray(blockRes.data)
      ? blockRes.data
      : (blockRes.data as unknown as { content: ContentBlockResponse[] }).content ?? []
    // SEO 폼 초기화
    const p = pageRes.data
    seoForm.value = {
      seoTitle: p.seoTitle ?? '',
      seoDescription: p.seoDescription ?? '',
      seoKeywords: p.seoKeywords ?? '',
      ogImageUrl: p.ogImageUrl ?? '',
      canonicalUrl: p.canonicalUrl ?? '',
    }
  } catch {
    ElMessage.error(t('content.page.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function saveSeo(): Promise<void> {
  if (!page.value) return
  savingSeo.value = true
  try {
    // 현재 버전(currentVersion)을 expectedVersion으로 전달하여 낙관적 락 검증
    await pages.updateSeo(page.value.id, {
      seoTitle: seoForm.value.seoTitle || undefined,
      seoDescription: seoForm.value.seoDescription || undefined,
      seoKeywords: seoForm.value.seoKeywords || undefined,
      ogImageUrl: seoForm.value.ogImageUrl || undefined,
      canonicalUrl: seoForm.value.canonicalUrl || undefined,
    }, page.value.currentVersion)
    ElMessage.success(t('content.page.editor.seoSaved'))
  } catch (e) {
    if (handleConflict(e)) return
    ElMessage.error(t('content.page.editor.seoError'))
  } finally {
    savingSeo.value = false
  }
}

// 409 REVISION_CONFLICT 감지 시 충돌 모달 표시 (true 반환 = 충돌 처리됨)
function handleConflict(e: unknown): boolean {
  if (
    axios.isAxiosError(e) &&
    e.response?.status === 409 &&
    (e.response.data as RevisionConflictPayload | undefined)?.code === 'REVISION_CONFLICT'
  ) {
    conflictVersion.value = (e.response.data as RevisionConflictPayload).currentVersion
    conflictVisible.value = true
    return true
  }
  return false
}

// 충돌 모달 "최신 버전 불러오기" → 페이지 재로드
async function reloadAfterConflict(): Promise<void> {
  conflictVisible.value = false
  await loadPage()
  ElMessage.info(t('revision.conflict.reload'))
}

async function saveBlocks(): Promise<void> {
  if (!page.value) return
  savingBlocks.value = true
  try {
    // 블록 순서 재정렬 저장 (각 블록의 내용은 ContentBlockEditor에서 개별 저장)
    const reorderItems = localBlocks.value.map((b, i) => ({ id: b.id ?? 0, sortOrder: i }))
    if (reorderItems.length > 0) {
      await pages.reorderBlocks(page.value.id, reorderItems)
    }
    ElMessage.success(t('content.page.editor.blocksSaved'))
  } catch {
    ElMessage.error(t('content.page.editor.blocksError'))
  } finally {
    savingBlocks.value = false
  }
}

async function publishPage(): Promise<void> {
  await ElMessageBox.confirm(
    t('content.page.action.publishConfirm'),
    t('common.confirm'),
    { type: 'warning' }
  ).catch(() => { throw new Error('cancelled') })
  actioning.value = true
  try {
    await pages.publish(pageId.value)
    ElMessage.success(t('content.page.action.publishSuccess'))
    await loadPage()
  } catch (e: unknown) {
    if ((e as Error).message !== 'cancelled') {
      ElMessage.error(t('content.page.action.publishError'))
    }
  } finally {
    actioning.value = false
  }
}

async function retractPage(): Promise<void> {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      t('content.page.action.retractReason'),
      t('content.page.action.retract'),
      { inputPlaceholder: t('content.page.action.retractReasonHint') }
    )
    reason = result.value
  } catch {
    return // 취소
  }
  actioning.value = true
  try {
    await pages.retract(pageId.value, reason)
    ElMessage.success(t('content.page.action.retractSuccess'))
    await loadPage()
  } catch {
    ElMessage.error(t('content.page.action.retractError'))
  } finally {
    actioning.value = false
  }
}

async function cancelSchedule(): Promise<void> {
  await ElMessageBox.confirm(
    t('content.page.action.cancelScheduleConfirm'),
    t('common.confirm'),
    { type: 'warning' }
  ).catch(() => { throw new Error('cancelled') })
  actioning.value = true
  try {
    await pages.cancelSchedule(pageId.value)
    ElMessage.success(t('content.page.action.cancelScheduleSuccess'))
    await loadPage()
  } catch (e: unknown) {
    if ((e as Error).message !== 'cancelled') {
      ElMessage.error(t('content.page.action.cancelScheduleError'))
    }
  } finally {
    actioning.value = false
  }
}

async function confirmSchedule(): Promise<void> {
  if (!scheduleAt.value) return
  actioning.value = true
  try {
    await pages.schedule(pageId.value, scheduleAt.value.toISOString())
    ElMessage.success(t('content.page.action.scheduleSuccess'))
    scheduleOpen.value = false
    await loadPage()
  } catch {
    ElMessage.error(t('content.page.action.scheduleError'))
  } finally {
    actioning.value = false
  }
}

async function generatePreview(): Promise<void> {
  generatingToken.value = true
  try {
    const res = await pages.generatePreviewToken(pageId.value)
    previewUrl.value = res.data.previewUrl
  } catch {
    ElMessage.error(t('content.page.editor.previewError'))
  } finally {
    generatingToken.value = false
  }
}

async function onRolledBack(): Promise<void> {
  historyOpen.value = false
  await loadPage()
  ElMessage.success(t('content.page.action.rollbackSuccess'))
}

// 페이지 ID 변경 시 재로드
watch(pageId, loadPage)
</script>
