<template>
  <!-- JSON 타입 설정값 편집기 — SPEC-CMS-005 REQ-SYS-004-D -->
  <div>
    <el-input
      v-model="raw"
      type="textarea"
      :rows="6"
      :placeholder="t('system.setting.jsonPlaceholder')"
      :class="{ 'border-red-400': jsonError }"
      @input="onInput"
    />
    <p v-if="jsonError" class="mt-1 text-xs text-red-600">{{ jsonError }}</p>
    <p v-else-if="raw && !jsonError" class="mt-1 text-xs text-green-600">
      {{ t('system.setting.jsonValid') }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  /** JSON 파싱 결과 (유효할 때만 emit) */
  'valid': [parsed: unknown]
}>()

const { t } = useI18n()
const raw = ref(props.modelValue)
const jsonError = ref<string | null>(null)

watch(() => props.modelValue, v => {
  if (v !== raw.value) raw.value = v
})

function onInput(): void {
  emit('update:modelValue', raw.value)
  if (!raw.value.trim()) {
    jsonError.value = null
    return
  }
  try {
    const parsed = JSON.parse(raw.value)
    jsonError.value = null
    emit('valid', parsed)
  } catch (e) {
    jsonError.value = e instanceof SyntaxError ? e.message : t('system.setting.jsonInvalid')
  }
}
</script>
