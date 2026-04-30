<template>
  <!-- 팝업 위치 시각화 — SPEC-CMS-004 REQ-CONTENT-008-D-1 -->
  <div class="relative mx-auto rounded border border-gray-300 bg-gray-100"
       style="width:320px; height:200px;"
       :aria-label="t('content.popup.previewAriaLabel')"
       role="img">
    <div class="absolute inset-0 flex items-center justify-center text-xs text-gray-400">
      {{ t('content.popup.previewSite') }}
    </div>

    <!-- 팝업 박스 시각화 -->
    <div
      class="absolute rounded border-2 border-blue-500 bg-blue-50 flex items-center justify-center text-xs text-blue-700 font-medium shadow"
      :style="boxStyle"
      :aria-label="t('content.popup.previewPopup')"
    >
      {{ t('content.popup.previewPopup') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PopupPosition } from '@/api/content'

const { t } = useI18n()

const props = defineProps<{
  position: PopupPosition
  posX?: number
  posY?: number
  width?: number
  height?: number
}>()

// 320x200 비율 스케일링 — 실제 팝업은 원래 사이즈로 표시
const SCALE = 0.4

const boxStyle = computed(() => {
  const w = Math.max(40, (props.width ?? 200) * SCALE)
  const h = Math.max(30, (props.height ?? 150) * SCALE)
  const base = { width: `${w}px`, height: `${h}px` }

  if (props.position === 'CUSTOM') {
    return {
      ...base,
      left: `${(props.posX ?? 0) * SCALE}px`,
      top: `${(props.posY ?? 0) * SCALE}px`,
    }
  }

  const positions: Record<PopupPosition, Record<string, string>> = {
    CENTER: { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' },
    TOP_RIGHT: { top: '4px', right: '4px' },
    BOTTOM_RIGHT: { bottom: '4px', right: '4px' },
    TOP_LEFT: { top: '4px', left: '4px' },
    BOTTOM_LEFT: { bottom: '4px', left: '4px' },
    CUSTOM: {},
  }

  return { ...base, ...positions[props.position] }
})
</script>
