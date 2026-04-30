<template>
  <!-- SEO 리다이렉트 관리 — SPEC-CMS-004 REQ-CONTENT-009-D -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('content.seo.title') }}</h2>
      <el-button type="primary" @click="openCreate">+ {{ t('content.seo.add') }}</el-button>
    </div>

    <!-- 검색 -->
    <div class="mb-4 flex gap-3">
      <el-input
        v-model="search"
        :placeholder="t('content.seo.searchPlaceholder')"
        clearable
        style="width:280px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-button @click="load">{{ t('common.search') }}</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="redirectList"
      stripe
      :empty-text="t('content.seo.empty')"
      :aria-label="t('content.seo.title')"
    >
      <caption class="sr-only">{{ t('content.seo.title') }}</caption>
      <el-table-column prop="fromPath" :label="t('content.seo.field.fromPath')" min-width="200">
        <template #default="{ row }">
          <span class="font-mono text-xs">{{ row.fromPath }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="toPath" :label="t('content.seo.field.toPath')" min-width="200">
        <template #default="{ row }">
          <span class="font-mono text-xs">{{ row.toPath }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="httpStatus" :label="t('content.seo.field.httpStatus')" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.httpStatus === 301 ? 'success' : 'warning'" size="small">
            {{ row.httpStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('content.seo.field.isActive')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
            {{ row.isActive ? t('common.active') : t('common.inactive') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" plain @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-popconfirm :title="t('content.seo.deleteConfirm')" @confirm="deleteRedirect(row)">
            <template #reference>
              <el-button size="small" type="danger" plain>{{ t('common.delete') }}</el-button>
            </template>
          </el-popconfirm>
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
        @current-change="load"
        :aria-label="t('a11y.pagination')"
      />
    </div>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="dialogOpen"
      :title="editingId ? t('content.seo.editDialog.title') : t('content.seo.createDialog.title')"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        @submit.prevent="save"
      >
        <el-form-item :label="t('content.seo.field.fromPath')" prop="fromPath">
          <el-input v-model="form.fromPath" placeholder="/old-path" class="font-mono" />
          <div class="mt-1 text-xs text-gray-400">{{ t('content.seo.field.fromPathHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('content.seo.field.toPath')" prop="toPath">
          <el-input v-model="form.toPath" placeholder="/new-path or https://..." class="font-mono" />
        </el-form-item>
        <el-form-item :label="t('content.seo.field.httpStatus')" prop="httpStatus">
          <el-select v-model="form.httpStatus" class="w-full">
            <el-option :label="t('content.seo.status.301')" :value="301" />
            <el-option :label="t('content.seo.status.302')" :value="302" />
            <el-option :label="t('content.seo.status.307')" :value="307" />
            <el-option :label="t('content.seo.status.308')" :value="308" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('content.seo.field.isActive')">
          <el-switch v-model="form.isActive" :aria-label="t('content.seo.field.isActive')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { seoRedirects } from '@/api/content'
import type { SeoRedirectResponse } from '@/api/content'
import { useSiteStore } from '@/stores/content'

const { t } = useI18n()
const siteStore = useSiteStore()

const redirectList = ref<SeoRedirectResponse[]>([])
const loading = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const search = ref('')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)

const form = ref({
  fromPath: '',
  toPath: '',
  httpStatus: 301 as number,
  isActive: true,
})

const rules: FormRules = {
  fromPath: [
    { required: true, message: t('content.seo.error.fromPathRequired'), trigger: 'blur' },
    { pattern: /^\//, message: t('content.seo.error.fromPathPattern'), trigger: 'blur' },
  ],
  toPath: [
    { required: true, message: t('content.seo.error.toPathRequired'), trigger: 'blur' },
  ],
  httpStatus: [
    { required: true, message: t('content.seo.error.httpStatusRequired'), trigger: 'change' },
  ],
}

onMounted(async () => {
  await siteStore.fetchCurrent()
  await load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const siteId = siteStore.currentSite?.id
    const res = await seoRedirects.list({
      siteId,
      page: currentPage.value - 1,
      size: pageSize,
      search: search.value || undefined,
    })
    const data = res.data as unknown as { content: SeoRedirectResponse[]; totalElements: number }
    redirectList.value = data.content ?? []
    total.value = data.totalElements ?? 0
  } catch {
    ElMessage.error(t('content.seo.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.value = { fromPath: '', toPath: '', httpStatus: 301, isActive: true }
  dialogOpen.value = true
}

function openEdit(row: SeoRedirectResponse): void {
  editingId.value = row.id
  form.value = {
    fromPath: row.fromPath,
    toPath: row.toPath,
    httpStatus: row.httpStatus,
    isActive: row.isActive,
  }
  dialogOpen.value = true
}

async function save(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const siteId = siteStore.currentSite?.id
  if (!siteId) return
  saving.value = true
  try {
    const payload = {
      siteId,
      fromPath: form.value.fromPath,
      toPath: form.value.toPath,
      httpStatus: form.value.httpStatus,
      isActive: form.value.isActive,
    }
    if (editingId.value) {
      await seoRedirects.update(editingId.value, payload)
    } else {
      await seoRedirects.create(payload)
    }
    ElMessage.success(t('content.seo.saved'))
    dialogOpen.value = false
    await load()
  } catch {
    ElMessage.error(t('content.seo.saveError'))
  } finally {
    saving.value = false
  }
}

async function deleteRedirect(row: SeoRedirectResponse): Promise<void> {
  try {
    await seoRedirects.delete(row.id)
    ElMessage.success(t('content.seo.deleted'))
    await load()
  } catch {
    ElMessage.error(t('content.seo.deleteError'))
  }
}
</script>
