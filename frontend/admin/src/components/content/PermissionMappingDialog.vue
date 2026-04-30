<template>
  <!-- 메뉴 권한 매핑 다이얼로그 — SPEC-CMS-004 REQ-CONTENT-002-D-1 -->
  <el-dialog
    v-model="visible"
    :title="t('content.menu.permissionDialog.title')"
    width="500px"
    :close-on-click-modal="false"
    :aria-label="t('content.menu.permissionDialog.title')"
  >
    <p class="mb-3 text-sm text-gray-600">{{ t('content.menu.permissionDialog.desc') }}</p>

    <el-select
      v-model="selected"
      multiple
      filterable
      :placeholder="t('content.menu.permissionDialog.placeholder')"
      class="w-full"
      :aria-label="t('content.menu.permissionDialog.title')"
    >
      <el-option
        v-for="perm in permissionOptions"
        :key="perm"
        :label="perm"
        :value="perm"
      />
    </el-select>

    <template #footer>
      <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { menus } from '@/api/content'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  menuId: number | null
  currentCodes: string[]
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'saved'): void
}>()

// SPEC-CMS-002 권한 코드 목록 (프런트엔드 상수 — 실제로는 API에서 조회 가능하면 대체)
const permissionOptions = [
  'CONTENT:READ', 'CONTENT:WRITE',
  'MENU:READ', 'MENU:WRITE',
  'PAGE:READ', 'PAGE:WRITE', 'PAGE:PUBLISH',
  'POPUP:READ', 'POPUP:WRITE',
  'BANNER:READ', 'BANNER:WRITE',
  'SYSTEM:READ', 'SYSTEM:ADMIN',
  'ROLE:READ', 'ROLE:WRITE',
  'USER:READ', 'USER:WRITE',
  'AUDIT:READ',
]

const visible = ref(props.modelValue)
const selected = ref<string[]>([...props.currentCodes])
const saving = ref(false)

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => emit('update:modelValue', val))
watch(() => props.currentCodes, (codes) => { selected.value = [...codes] })

async function save(): Promise<void> {
  if (!props.menuId) return
  saving.value = true
  try {
    await menus.replacePermissions(props.menuId, selected.value)
    ElMessage.success(t('content.menu.permissionDialog.saved'))
    emit('saved')
    visible.value = false
  } catch {
    ElMessage.error(t('content.menu.permissionDialog.error'))
  } finally {
    saving.value = false
  }
}
</script>
