<template>
  <!-- 팝업 관리 — SPEC-CMS-004 REQ-CONTENT-006-D -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('content.popup.title') }}</h2>
      <el-button type="primary" @click="openCreate">+ {{ t('content.popup.add') }}</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="popupList"
      stripe
      :empty-text="t('content.popup.empty')"
      :aria-label="t('content.popup.title')"
    >
      <caption class="sr-only">{{ t('content.popup.title') }}</caption>
      <el-table-column prop="name" :label="t('content.popup.field.name')" min-width="160" />
      <el-table-column :label="t('content.popup.field.position')" width="130">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.position }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('content.popup.field.period')" min-width="200">
        <template #default="{ row }">
          <span class="text-xs">
            {{ formatDate(row.showFrom) }} ~ {{ row.showUntil ? formatDate(row.showUntil) : '∞' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="t('content.popup.field.isActive')" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.isActive"
            size="small"
            :aria-label="t('content.popup.field.isActive')"
            @change="toggleActive(row)"
          />
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" plain @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-popconfirm :title="t('content.popup.deleteConfirm')" @confirm="deletePopup(row)">
            <template #reference>
              <el-button size="small" type="danger" plain>{{ t('common.delete') }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="dialogOpen"
      :title="editingId ? t('content.popup.editDialog.title') : t('content.popup.createDialog.title')"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="140px"
        @submit.prevent="save"
      >
        <el-form-item :label="t('content.popup.field.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('content.popup.field.contentHtml')" prop="contentHtml">
          <el-input v-model="form.contentHtml" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item :label="t('content.popup.field.position')" prop="position">
          <el-select v-model="form.position" class="w-full">
            <el-option v-for="p in positions" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>

        <!-- CUSTOM 포지션일 때만 x/y 좌표 표시 -->
        <template v-if="form.position === 'CUSTOM'">
          <el-form-item :label="t('content.popup.field.posX')" prop="posX">
            <el-input-number v-model="form.posX" :min="0" :max="100" />
            <span class="ml-2 text-xs text-gray-400">%</span>
          </el-form-item>
          <el-form-item :label="t('content.popup.field.posY')" prop="posY">
            <el-input-number v-model="form.posY" :min="0" :max="100" />
            <span class="ml-2 text-xs text-gray-400">%</span>
          </el-form-item>
        </template>

        <el-form-item :label="t('content.popup.field.width')" prop="width">
          <el-input-number v-model="form.width" :min="100" :max="1200" />
          <span class="ml-2 text-xs text-gray-400">px</span>
        </el-form-item>
        <el-form-item :label="t('content.popup.field.showFrom')" prop="showFrom">
          <el-date-picker
            v-model="form.showFrom"
            type="datetime"
            :placeholder="t('content.popup.field.showFrom')"
            class="w-full"
          />
        </el-form-item>
        <el-form-item :label="t('content.popup.field.showUntil')">
          <el-date-picker
            v-model="form.showUntil"
            type="datetime"
            :placeholder="t('content.popup.field.showUntilHint')"
            :disabled-date="disabledUntilDate"
            class="w-full"
            clearable
          />
          <div class="mt-1 text-xs text-gray-400">{{ t('content.popup.field.showUntilHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('content.popup.field.targetType')" prop="targetType">
          <el-select v-model="form.targetType" class="w-full" @change="onTargetTypeChange">
            <el-option v-for="tt in targetTypes" :key="tt" :label="tt" :value="tt" />
          </el-select>
        </el-form-item>

        <!-- ROLE 타입일 때 역할 코드 선택 -->
        <el-form-item v-if="form.targetType === 'ROLE'" :label="t('content.popup.field.targetRoleCodes')">
          <el-select v-model="form.targetRoleCodes" multiple class="w-full">
            <el-option v-for="r in roleCodes" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>

        <!-- 미리보기 -->
        <el-form-item :label="t('content.popup.preview')">
          <PopupPositionPreview :position="form.position" :pos-x="form.posX ?? 0" :pos-y="form.posY ?? 0" />
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
import { popups } from '@/api/content'
import type { PopupResponse, PopupTargetType } from '@/api/content'
import { useSiteStore } from '@/stores/content'
import PopupPositionPreview from '@/components/content/PopupPositionPreview.vue'

const { t } = useI18n()
const siteStore = useSiteStore()

const positions = ['CENTER', 'TOP_RIGHT', 'BOTTOM_RIGHT', 'TOP_LEFT', 'BOTTOM_LEFT', 'CUSTOM'] as const
const targetTypes: PopupTargetType[] = ['ALL', 'ANONYMOUS', 'AUTHENTICATED', 'ROLE']
const roleCodes = ['SUPER_ADMIN', 'SYSADMIN', 'CONTENT_EDITOR', 'BOARD_MANAGER', 'VIEWER'] as const

const popupList = ref<PopupResponse[]>([])
const loading = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = ref({
  name: '',
  contentHtml: '',
  position: 'CENTER' as typeof positions[number],
  posX: 0,
  posY: 0,
  width: 400,
  showFrom: null as Date | null,
  showUntil: null as Date | null,
  targetType: 'ALL' as PopupTargetType,
  targetRoleCodes: [] as string[],
})

const rules: FormRules = {
  name: [{ required: true, message: t('content.popup.error.nameRequired'), trigger: 'blur' }],
  contentHtml: [{ required: true, message: t('content.popup.error.contentRequired'), trigger: 'blur' }],
  position: [{ required: true, message: t('content.popup.error.positionRequired'), trigger: 'change' }],
  showFrom: [{ required: true, message: t('content.popup.error.showFromRequired'), trigger: 'change' }],
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString()
}

function disabledUntilDate(date: Date): boolean {
  if (!form.value.showFrom) return false
  return date <= new Date(form.value.showFrom)
}

function onTargetTypeChange(): void {
  if (form.value.targetType !== 'ROLE') {
    form.value.targetRoleCodes = []
  }
}

onMounted(async () => {
  await siteStore.fetchCurrent()
  await loadPopups()
})

async function loadPopups(): Promise<void> {
  loading.value = true
  try {
    const siteId = siteStore.currentSite?.id
    const res = await popups.list(siteId)
    popupList.value = Array.isArray(res.data)
      ? res.data
      : (res.data as unknown as { content: PopupResponse[] }).content ?? []
  } catch {
    ElMessage.error(t('content.popup.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.value = {
    name: '',
    contentHtml: '',
    position: 'CENTER',
    posX: 0,
    posY: 0,
    width: 400,
    showFrom: null,
    showUntil: null,
    targetType: 'ALL',
    targetRoleCodes: [],
  }
  dialogOpen.value = true
}

function openEdit(row: PopupResponse): void {
  editingId.value = row.id
  form.value = {
    name: row.name,
    contentHtml: row.contentHtml,
    position: row.position,
    posX: row.posX ?? 0,
    posY: row.posY ?? 0,
    width: row.width,
    showFrom: row.showFrom ? new Date(row.showFrom) : null,
    showUntil: row.showUntil ? new Date(row.showUntil) : null,
    targetType: row.targetType as PopupTargetType,
    targetRoleCodes: row.targetRoleCodes ?? [],
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
      name: form.value.name,
      contentHtml: form.value.contentHtml,
      position: form.value.position,
      posX: form.value.position === 'CUSTOM' ? form.value.posX : undefined,
      posY: form.value.position === 'CUSTOM' ? form.value.posY : undefined,
      width: form.value.width,
      showFrom: form.value.showFrom?.toISOString() ?? new Date().toISOString(),
      showUntil: form.value.showUntil?.toISOString() ?? undefined,
      targetType: form.value.targetType,
      targetRoleCodes: form.value.targetType === 'ROLE' ? form.value.targetRoleCodes : undefined,
    }
    if (editingId.value) {
      await popups.update(editingId.value, payload)
    } else {
      await popups.create(payload)
    }
    ElMessage.success(t('content.popup.saved'))
    dialogOpen.value = false
    await loadPopups()
  } catch {
    ElMessage.error(t('content.popup.saveError'))
  } finally {
    saving.value = false
  }
}

async function toggleActive(row: PopupResponse): Promise<void> {
  try {
    await popups.setActive(row.id, !row.isActive)
    await loadPopups()
  } catch {
    ElMessage.error(t('content.popup.toggleError'))
  }
}

async function deletePopup(row: PopupResponse): Promise<void> {
  try {
    await popups.delete(row.id)
    ElMessage.success(t('content.popup.deleted'))
    await loadPopups()
  } catch {
    ElMessage.error(t('content.popup.deleteError'))
  }
}
</script>
