<script setup lang="ts">
// SPEC-CMS-DASHBOARD-PERSONALIZE-001 REQ-DP-003 — 드래그앤드롭 위젯 그리드 래퍼
// grid-layout-plus (Vue 3 fork of vue-grid-layout, MIT) 통합
//
// 사용 예:
//   <DashboardGridLayout
//     :layout-id="layout.id"
//     :widgets="visibleWidgets"
//     :editable="isEditMode"
//     :expected-updated-at="layout.updated_at"
//   >
//     <template #widget="{ widget }">
//       <YourWidgetCard :widget="widget" />
//     </template>
//   </DashboardGridLayout>
//
// 제약: AC-DP-003-3 — 클라이언트 사전 검증으로 겹침 방지 (preventCollision)
// 디바운스: 1초 (REQ-DP-003-2)
// 모바일: width < 768px → 편집 모드 강제 OFF (AC-DP-003-4)
//
// @MX:NOTE: [AUTO] DashboardGridLayout — DnD 결과 영속화의 단일 채널
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 REQ-DP-003
import { computed, onMounted, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { Component } from 'vue'
import { ElMessage } from 'element-plus'
import { useDashboardPreferenceStore } from '@/stores/dashboardPreferenceStore'

export interface GridWidget {
  /** dashboard_layout_widget.instance_id (string PK 일부) */
  instanceId: string
  /** dashboard_widget.id */
  widgetId: number
  /** {x,y,w,h} 12-grid 좌표 */
  position: { x: number; y: number; w: number; h: number }
  /** UI 라벨 (옵션) */
  name?: string
}

interface Props {
  layoutId: number
  widgets: GridWidget[]
  editable: boolean
  /** REQ-DP-003-5 낙관적 잠금 (옵션). layout.updated_at 을 그대로 전달. */
  expectedUpdatedAt?: string
  /** 모바일 강제 OFF 임계값 (기본 768px) */
  mobileBreakpointPx?: number
}

const props = withDefaults(defineProps<Props>(), {
  expectedUpdatedAt: undefined,
  mobileBreakpointPx: 768,
})

const store = useDashboardPreferenceStore()

// AC-DP-003-4: 모바일에서 편집 모드 강제 OFF
const isMobile = ref(
  typeof window !== 'undefined' ? window.innerWidth < props.mobileBreakpointPx : false,
)
const onResize = () => {
  isMobile.value = window.innerWidth < props.mobileBreakpointPx
}
onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', onResize)
  }
})
onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', onResize)
  }
})

const effectiveEditable = computed(() => props.editable && !isMobile.value)

// grid-layout-plus 형식의 layout 배열
const gridLayout = ref(
  props.widgets.map((w) => ({
    i: w.instanceId,
    x: w.position.x,
    y: w.position.y,
    w: w.position.w,
    h: w.position.h,
  })),
)

watch(
  () => props.widgets,
  (ws) => {
    gridLayout.value = ws.map((w) => ({
      i: w.instanceId,
      x: w.position.x,
      y: w.position.y,
      w: w.position.w,
      h: w.position.h,
    }))
  },
  { deep: true },
)

// ── grid-layout-plus 동적 로드 (ESM 호환, 미설치 시 fallback) ────────────────
const GridLayoutComponent = shallowRef<Component | null>(null)
const GridItemComponent = shallowRef<Component | null>(null)

onMounted(async () => {
  try {
    const mod = await import('grid-layout-plus')
    GridLayoutComponent.value = (mod as unknown as { GridLayout: Component }).GridLayout
    GridItemComponent.value = (mod as unknown as { GridItem: Component }).GridItem
  } catch {
    // 패키지 미설치 — fallback-grid 가 렌더됨. SPEC §11 외부 의존성 가이드 참조.
  }
})

// REQ-DP-003-2: 1초 디바운스 자동 저장
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function scheduleSave(): void {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    void persistPositions()
  }, 1000)
}

async function persistPositions(): Promise<void> {
  const entries = gridLayout.value.map((g) => ({
    instance_id: String(g.i),
    position: { x: g.x, y: g.y, w: g.w, h: g.h },
  }))
  try {
    await store.patchPositions(props.layoutId, entries, {
      expectedUpdatedAt: props.expectedUpdatedAt,
    })
  } catch (e) {
    // AC-DP-003-5: 충돌 시 새로고침 안내
    ElMessage.error(
      e instanceof Error && e.message.includes('Conflict')
        ? '다른 탭에서 변경되었습니다. 새로고침해 주세요.'
        : `위젯 위치 저장 실패: ${e instanceof Error ? e.message : String(e)}`,
    )
  }
}

// grid-layout-plus 의 layout-updated 이벤트 핸들러
function onLayoutUpdated(
  newLayout: Array<{ i: string | number; x: number; y: number; w: number; h: number }>,
): void {
  gridLayout.value = newLayout.map((g) => ({
    i: g.i,
    x: g.x,
    y: g.y,
    w: g.w,
    h: g.h,
  }))
  scheduleSave()
}

// 내부 노출 (vitest 에서 호출)
defineExpose({
  persistPositions,
  scheduleSave,
  effectiveEditable,
})
</script>

<template>
  <div data-testid="dashboard-grid-layout" :class="{ 'is-editing': effectiveEditable }">
    <component
      :is="GridLayoutComponent"
      v-if="GridLayoutComponent && GridItemComponent"
      :layout="gridLayout"
      :col-num="12"
      :row-height="80"
      :is-draggable="effectiveEditable"
      :is-resizable="effectiveEditable"
      :prevent-collision="true"
      :vertical-compact="false"
      :margin="[16, 16]"
      :use-css-transforms="true"
      @layout-updated="onLayoutUpdated"
    >
      <component
        :is="GridItemComponent"
        v-for="w in widgets"
        :key="w.instanceId"
        :i="w.instanceId"
        :x="w.position.x"
        :y="w.position.y"
        :w="w.position.w"
        :h="w.position.h"
      >
        <slot name="widget" :widget="w" />
      </component>
    </component>

    <!-- 폴백: grid-layout-plus 미설치 환경 (테스트 + 초기 빌드) -->
    <div v-else class="fallback-grid">
      <div
        v-for="w in widgets"
        :key="w.instanceId"
        class="fallback-grid-item"
        :data-instance-id="w.instanceId"
      >
        <slot name="widget" :widget="w" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.fallback-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 16px;
}
.fallback-grid-item {
  grid-column: span 4;
}
</style>
