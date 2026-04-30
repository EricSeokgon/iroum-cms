<template>
  <!-- 페이지 목록 — SPEC-CMS-004 REQ-CONTENT-005-D -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('content.page.list.title') }}</h2>
      <el-button type="primary" @click="openCreate">+ {{ t('content.page.list.add') }}</el-button>
    </div>

    <!-- 필터 -->
    <div class="mb-4 flex gap-3">
      <el-select
        v-model="filterStatus"
        clearable
        :placeholder="t('content.page.list.filterStatus')"
        style="width:160px"
        @change="loadPages"
      >
        <el-option
          v-for="s in statusOptions"
          :key="s"
          :label="t(`content.page.status.${s}`)"
          :value="s"
        />
      </el-select>
      <el-input
        v-model="search"
        :placeholder="t('common.search')"
        clearable
        style="width:240px"
        @keyup.enter="loadPages"
        @clear="loadPages"
      />
      <el-button @click="loadPages">{{ t('common.search') }}</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="pageList"
      stripe
      :empty-text="t('content.page.list.empty')"
      :aria-label="t('content.page.list.title')"
    >
      <caption class="sr-only">{{ t('content.page.list.title') }}</caption>
      <el-table-column prop="slug" :label="t('content.page.field.slug')" min-width="180" />
      <el-table-column prop="title" :label="t('content.page.field.title')" min-width="200" />
      <el-table-column :label="t('content.page.field.status')" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`content.page.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentVersion" :label="t('content.page.field.version')" width="80" align="center" />
      <el-table-column :label="t('content.page.field.updatedAt')" width="160">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="230" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="goEdit(row.id)">{{ t('common.edit') }}</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            size="small"
            type="success"
            plain
            @click="publishPage(row)"
          >{{ t('content.page.action.publish') }}</el-button>
          <el-button
            v-if="row.status === 'DRAFT' || row.status === 'RETRACTED'"
            size="small"
            type="warning"
            plain
            @click="openSchedule(row)"
          >{{ t('content.page.action.schedule') }}</el-button>
          <el-button
            v-if="row.status === 'PUBLISHED'"
            size="small"
            type="danger"
            plain
            @click="retractPage(row)"
          >{{ t('content.page.action.retract') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadPages"
        :aria-label="t('a11y.pagination')"
      />
    </div>

    <!-- 신규 페이지 생성 다이얼로그 -->
    <el-dialog
      v-model="createOpen"
      :title="t('content.page.createDialog.title')"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="110px"
      >
        <el-form-item :label="t('content.page.field.code')" prop="code">
          <el-input v-model="createForm.code" />
        </el-form-item>
        <el-form-item :label="t('content.page.field.title')" prop="title">
          <el-input v-model="createForm.title" />
        </el-form-item>
        <el-form-item :label="t('content.page.field.slug')" prop="slug">
          <el-input v-model="createForm.slug" placeholder="example-page" />
          <div class="mt-1 text-xs text-gray-400">{{ t('content.page.field.slugHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('content.page.field.template')" prop="templateId">
          <el-select v-model="createForm.templateId" class="w-full" value-key="id">
            <el-option
              v-for="tmpl in templateOptions"
              :key="tmpl.id"
              :label="`${tmpl.name} (${tmpl.code})`"
              :value="tmpl.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="createPage">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

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
        <el-button type="primary" :loading="scheduling" @click="confirmSchedule">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { pages, templates } from '@/api/content'
import type { PageItemResponse as PageType, TemplateResponse, PageStatus } from '@/api/content'
import { useSiteStore } from '@/stores/content'

const { t } = useI18n()
const router = useRouter()
const siteStore = useSiteStore()

const statusOptions: PageStatus[] = ['DRAFT', 'SCHEDULED', 'PUBLISHED', 'RETRACTED']

const pageList = ref<PageType[]>([])
const templateOptions = ref<TemplateResponse[]>([])
const loading = ref(false)
const creating = ref(false)
const scheduling = ref(false)
const filterStatus = ref<PageStatus | ''>('')
const search = ref('')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)
const createOpen = ref(false)
const scheduleOpen = ref(false)
const scheduleAt = ref<Date | null>(null)
const schedulingPageId = ref<number | null>(null)
const createFormRef = ref<FormInstance>()

const createForm = ref({
  code: '',
  title: '',
  slug: '',
  templateId: null as number | null,
})

const createRules: FormRules = {
  code: [{ required: true, message: t('content.page.error.codeRequired'), trigger: 'blur' }],
  title: [{ required: true, message: t('content.page.error.titleRequired'), trigger: 'blur' }],
  slug: [
    { required: true, message: t('content.page.error.slugRequired'), trigger: 'blur' },
    { pattern: /^[a-z0-9][a-z0-9\-/]*$/, message: t('content.page.error.slugPattern'), trigger: 'blur' },
  ],
  templateId: [{ required: true, message: t('content.page.error.templateRequired'), trigger: 'change' }],
}

function statusTagType(status: PageStatus): '' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<PageStatus, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    DRAFT: 'info',
    SCHEDULED: 'warning',
    PUBLISHED: 'success',
    RETRACTED: 'danger',
  }
  return map[status]
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString()
}

function disabledDate(date: Date): boolean {
  return date < new Date()
}

onMounted(async () => {
  await siteStore.fetchCurrent()
  await Promise.all([loadPages(), loadTemplates()])
})

async function loadPages(): Promise<void> {
  loading.value = true
  try {
    const res = await pages.list({
      siteId: siteStore.currentSite?.id,
      status: filterStatus.value || undefined,
      page: currentPage.value - 1,
      size: pageSize,
      search: search.value || undefined,
    })
    const data = (res.data as unknown) as { content: PageType[]; totalElements: number }
    pageList.value = data.content ?? []
    total.value = data.totalElements ?? 0
  } catch {
    ElMessage.error(t('content.page.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadTemplates(): Promise<void> {
  try {
    const res = await templates.list()
    templateOptions.value = res.data.filter(t => t.status === 'ACTIVE')
  } catch { /* 조용히 실패 */ }
}

function goEdit(id: number): void {
  router.push({ name: 'content-page-edit', params: { id } })
}

function openCreate(): void {
  createForm.value = { code: '', title: '', slug: '', templateId: null }
  createOpen.value = true
}

async function createPage(): Promise<void> {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const siteId = siteStore.currentSite?.id
  if (!siteId || !createForm.value.templateId) return
  creating.value = true
  try {
    const res = await pages.create({
      siteId,
      templateId: createForm.value.templateId,
      code: createForm.value.code,
      title: createForm.value.title,
      slug: createForm.value.slug,
    })
    ElMessage.success(t('content.page.created'))
    createOpen.value = false
    router.push({ name: 'content-page-edit', params: { id: res.data.id } })
  } catch {
    ElMessage.error(t('content.page.createError'))
  } finally {
    creating.value = false
  }
}

async function publishPage(row: PageType): Promise<void> {
  await ElMessageBox.confirm(t('content.page.action.publishConfirm'), t('common.confirm'), {
    type: 'warning',
  }).catch(() => null)
  try {
    await pages.publish(row.id)
    ElMessage.success(t('content.page.action.publishSuccess'))
    await loadPages()
  } catch {
    ElMessage.error(t('content.page.action.publishError'))
  }
}

function openSchedule(row: PageType): void {
  schedulingPageId.value = row.id
  scheduleAt.value = null
  scheduleOpen.value = true
}

async function confirmSchedule(): Promise<void> {
  if (!schedulingPageId.value || !scheduleAt.value) return
  scheduling.value = true
  try {
    await pages.schedule(schedulingPageId.value, scheduleAt.value.toISOString())
    ElMessage.success(t('content.page.action.scheduleSuccess'))
    scheduleOpen.value = false
    await loadPages()
  } catch {
    ElMessage.error(t('content.page.action.scheduleError'))
  } finally {
    scheduling.value = false
  }
}

async function retractPage(row: PageType): Promise<void> {
  let reason = ''
  await ElMessageBox.prompt(t('content.page.action.retractReason'), t('content.page.action.retract'), {
    inputPlaceholder: t('content.page.action.retractReasonHint'),
  }).then(({ value }) => { reason = value }).catch(() => null)
  try {
    await pages.retract(row.id, reason)
    ElMessage.success(t('content.page.action.retractSuccess'))
    await loadPages()
  } catch {
    ElMessage.error(t('content.page.action.retractError'))
  }
}
</script>
