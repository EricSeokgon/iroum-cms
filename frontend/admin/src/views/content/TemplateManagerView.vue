<template>
  <!-- 페이지 템플릿 관리 — SPEC-CMS-004 REQ-CONTENT-004-D -->
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('content.template.title') }}</h2>
      <el-button type="primary" @click="openCreate">+ {{ t('content.template.add') }}</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="templateList"
      stripe
      :empty-text="t('content.template.empty')"
      :aria-label="t('content.template.title')"
    >
      <caption class="sr-only">{{ t('content.template.title') }}</caption>
      <el-table-column prop="code" :label="t('content.template.field.code')" min-width="120" />
      <el-table-column prop="name" :label="t('content.template.field.name')" min-width="150" />
      <el-table-column prop="layoutType" :label="t('content.template.field.layoutType')" width="140">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.layoutType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('content.template.field.hasContentSlot')" width="100" align="center">
        <template #default="{ row }">
          <span :aria-label="hasContentSlot(row.htmlTemplate) ? t('common.yes') : t('common.no')">
            {{ hasContentSlot(row.htmlTemplate) ? '✓' : '✗' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('content.template.field.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ t(`content.template.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="160">
        <template #default="{ row }">
          <el-button size="small" plain @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button
            size="small"
            :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
            plain
            @click="toggleStatus(row)"
          >
            {{ row.status === 'ACTIVE' ? t('common.deactivate') : t('common.activate') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="dialogOpen"
      :title="editingId ? t('content.template.editDialog.title') : t('content.template.createDialog.title')"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
        @submit.prevent="save"
      >
        <el-form-item :label="t('content.template.field.code')" prop="code">
          <el-input v-model="form.code" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item :label="t('content.template.field.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('content.template.field.layoutType')" prop="layoutType">
          <el-select v-model="form.layoutType" class="w-full">
            <el-option v-for="lt in layoutTypes" :key="lt" :label="lt" :value="lt" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('content.template.field.htmlTemplate')" prop="htmlTemplate">
          <el-input
            v-model="form.htmlTemplate"
            type="textarea"
            :rows="8"
            :placeholder="t('content.template.field.htmlPlaceholder')"
            class="font-mono text-sm"
          />
          <div class="mt-1 text-xs" :class="hasContentSlot(form.htmlTemplate) ? 'text-green-600' : 'text-orange-500'">
            {{ hasContentSlot(form.htmlTemplate)
              ? t('content.template.field.slotOk')
              : t('content.template.field.slotMissing') }}
          </div>
        </el-form-item>
        <el-form-item :label="t('content.template.field.cssAssets')">
          <div v-for="(url, i) in form.cssAssets" :key="`css-${i}`" class="mb-1 flex gap-2">
            <el-input v-model="form.cssAssets[i]" size="small" placeholder="https://..." />
            <el-button size="small" type="danger" plain @click="form.cssAssets.splice(i, 1)">×</el-button>
          </div>
          <el-button size="small" @click="form.cssAssets.push('')">+ CSS URL</el-button>
        </el-form-item>
        <el-form-item :label="t('content.template.field.jsAssets')">
          <div v-for="(url, i) in form.jsAssets" :key="`js-${i}`" class="mb-1 flex gap-2">
            <el-input v-model="form.jsAssets[i]" size="small" placeholder="https://..." />
            <el-button size="small" type="danger" plain @click="form.jsAssets.splice(i, 1)">×</el-button>
          </div>
          <el-button size="small" @click="form.jsAssets.push('')">+ JS URL</el-button>
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
import { templates } from '@/api/content'
import type { TemplateResponse } from '@/api/content'

const { t } = useI18n()

const layoutTypes = ['FULL', 'SIDEBAR_LEFT', 'SIDEBAR_RIGHT', 'LANDING', 'BLANK'] as const

const templateList = ref<TemplateResponse[]>([])
const loading = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = ref({
  code: '',
  name: '',
  layoutType: 'FULL' as typeof layoutTypes[number],
  htmlTemplate: '',
  cssAssets: [] as string[],
  jsAssets: [] as string[],
})

const rules: FormRules = {
  code: [{ required: true, message: t('content.template.error.codeRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('content.template.error.nameRequired'), trigger: 'blur' }],
  htmlTemplate: [{ required: true, message: t('content.template.error.htmlRequired'), trigger: 'blur' }],
}

function hasContentSlot(html: string): boolean {
  return html.includes('{{CONTENT}}')
}

onMounted(loadTemplates)

async function loadTemplates(): Promise<void> {
  loading.value = true
  try {
    const res = await templates.list()
    templateList.value = res.data
  } catch {
    ElMessage.error(t('content.template.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.value = { code: '', name: '', layoutType: 'FULL', htmlTemplate: '', cssAssets: [], jsAssets: [] }
  dialogOpen.value = true
}

function openEdit(row: TemplateResponse): void {
  editingId.value = row.id
  form.value = {
    code: row.code,
    name: row.name,
    layoutType: row.layoutType,
    htmlTemplate: row.htmlTemplate,
    cssAssets: [...row.cssAssets],
    jsAssets: [...row.jsAssets],
  }
  dialogOpen.value = true
}

async function save(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      ...form.value,
      cssAssets: form.value.cssAssets.filter(Boolean),
      jsAssets: form.value.jsAssets.filter(Boolean),
    }
    if (editingId.value) {
      await templates.update(editingId.value, payload)
    } else {
      await templates.create(payload)
    }
    ElMessage.success(t('content.template.saved'))
    dialogOpen.value = false
    await loadTemplates()
  } catch {
    ElMessage.error(t('content.template.saveError'))
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: TemplateResponse): Promise<void> {
  const newStatus = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await templates.changeStatus(row.id, newStatus)
    ElMessage.success(t('content.template.statusChanged'))
    await loadTemplates()
  } catch (err: unknown) {
    const status = (err as { response?: { status?: number } })?.response?.status
    if (status === 409) {
      ElMessage.error(t('content.template.error.inUse'))
    } else {
      ElMessage.error(t('content.template.error.statusFailed'))
    }
  }
}
</script>
