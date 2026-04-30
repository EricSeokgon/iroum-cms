<template>
  <!-- 점검 모드 관리 — SPEC-CMS-005 Bundle D REQ-SYS-005-D -->
  <div>
    <!-- 활성 점검 알림 배너 -->
    <div
      v-if="activeMaintenance"
      class="mb-4 flex items-center gap-2 rounded-lg bg-yellow-50 border border-yellow-300 px-4 py-3"
      role="alert"
    >
      <el-icon class="text-yellow-600"><i-ep-warning /></el-icon>
      <span class="text-sm font-medium text-yellow-800">
        {{ t('system.maintenance.banner.active') }}: {{ activeMaintenance.message_ko }}
      </span>
    </div>

    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('system.maintenance.title') }}</h2>
      <el-button type="primary" @click="openCreate">+ {{ t('system.maintenance.add') }}</el-button>
    </div>

    <!-- 점검 목록 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="list" stripe size="small">
        <el-table-column :label="t('system.maintenance.col.period')" width="320">
          <template #default="scope">
            <template v-if="scope?.row">{{ formatDate(scope.row.start_at) }} ~ {{ formatDate(scope.row.end_at) }}</template>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.maintenance.col.status')" width="110" align="center">
          <template #default="scope">
            <el-tag v-if="scope?.row" :type="statusTagType(scope.row.status)" size="small">
              {{ t(`system.maintenance.status.${scope.row.status.toLowerCase()}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message_ko" :label="t('system.maintenance.col.message')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('system.maintenance.col.allowAdmin')" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope?.row" :type="scope.row.allow_admin_access ? 'success' : 'danger'" size="small">
              {{ scope.row.allow_admin_access ? t('common.yes') : t('common.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" align="center">
          <template #default="scope">
            <template v-if="scope?.row">
              <el-button size="small" plain @click="openEdit(scope.row)">{{ t('common.edit') }}</el-button>
              <el-button
                v-if="scope.row.status === 'SCHEDULED'"
                size="small"
                type="warning"
                plain
                @click="activate(scope.row)"
              >
                {{ t('system.maintenance.activate') }}
              </el-button>
              <el-button
                v-if="scope.row.status === 'SCHEDULED' || scope.row.status === 'ACTIVE'"
                size="small"
                type="danger"
                plain
                @click="cancel(scope.row)"
              >
                {{ t('system.maintenance.cancel') }}
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? t('system.maintenance.edit') : t('system.maintenance.add')"
      width="520px"
    >
      <el-form :model="form" :rules="rules" label-width="120px" ref="formRef">
        <el-form-item :label="t('system.maintenance.col.period')" prop="dateRange">
          <el-date-picker
            v-model="form.dateRange"
            type="datetimerange"
            :range-separator="t('common.to')"
            :start-placeholder="t('common.startDate')"
            :end-placeholder="t('common.endDate')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('system.maintenance.field.messageKo')" prop="message_ko">
          <el-input v-model="form.message_ko" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('system.maintenance.field.messageEn')" prop="message_en">
          <el-input v-model="form.message_en" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('system.maintenance.col.allowAdmin')">
          <el-switch
            v-model="form.allow_admin_access"
            :active-text="t('common.yes')"
            :inactive-text="t('common.no')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { maintenance } from '@/api/system'
import type { MaintenanceResponse, MaintenanceStatus } from '@/api/system'

const { t } = useI18n()

const list = ref<MaintenanceResponse[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editing = ref<MaintenanceResponse | null>(null)
const formRef = ref<FormInstance>()

const form = ref({
  dateRange: null as [string, string] | null,
  message_ko: '',
  message_en: '',
  allow_admin_access: true,
})

const rules: FormRules = {
  dateRange: [
    {
      validator: (_rule, val: [string, string] | null, cb) => {
        if (!val || val.length < 2) { cb(new Error(t('common.required'))); return }
        if (new Date(val[0]) >= new Date(val[1])) {
          cb(new Error(t('system.maintenance.validation.startBeforeEnd')))
        } else {
          cb()
        }
      },
      trigger: 'change',
    },
  ],
  message_ko: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  message_en: [{ required: true, message: t('common.required'), trigger: 'blur' }],
}

const activeMaintenance = computed(() => list.value.find(m => m.status === 'ACTIVE') ?? null)

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

function statusTagType(status: MaintenanceStatus): 'primary' | 'danger' | 'info' | '' {
  switch (status) {
    case 'SCHEDULED': return 'primary'
    case 'ACTIVE':    return 'danger'
    case 'COMPLETED': return 'info'
    case 'CANCELLED': return ''
    default: return ''
  }
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const res = await maintenance.list()
    list.value = res.data
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  form.value = { dateRange: null, message_ko: '', message_en: '', allow_admin_access: true }
  dialogVisible.value = true
}

function openEdit(row: MaintenanceResponse): void {
  editing.value = row
  form.value = {
    dateRange: [row.start_at, row.end_at],
    message_ko: row.message_ko,
    message_en: row.message_en,
    allow_admin_access: row.allow_admin_access,
  }
  dialogVisible.value = true
}

async function save(): Promise<void> {
  await formRef.value?.validate()
  if (!form.value.dateRange) return
  saving.value = true
  try {
    const payload = {
      start_at: form.value.dateRange[0],
      end_at: form.value.dateRange[1],
      message_ko: form.value.message_ko,
      message_en: form.value.message_en,
      allow_admin_access: form.value.allow_admin_access,
    }
    if (editing.value) {
      await maintenance.update(editing.value.id, payload)
    } else {
      await maintenance.create(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    dialogVisible.value = false
    await loadList()
  } catch {
    ElMessage.error(t('common.saveError'))
  } finally {
    saving.value = false
  }
}

async function activate(row: MaintenanceResponse): Promise<void> {
  await ElMessageBox.confirm(t('system.maintenance.activateConfirm'), t('common.confirm'), { type: 'warning' })
  try {
    await maintenance.activate(row.id)
    ElMessage.success(t('system.maintenance.activateSuccess'))
    await loadList()
  } catch {
    ElMessage.error(t('common.saveError'))
  }
}

async function cancel(row: MaintenanceResponse): Promise<void> {
  await ElMessageBox.confirm(t('system.maintenance.cancelConfirm'), t('common.confirm'), { type: 'warning' })
  try {
    await maintenance.cancel(row.id)
    ElMessage.success(t('system.maintenance.cancelSuccess'))
    await loadList()
  } catch {
    ElMessage.error(t('common.saveError'))
  }
}

onMounted(() => loadList())
</script>
