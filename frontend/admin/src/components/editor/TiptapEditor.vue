<template>
  <!--
    SPEC-CMS-003 — Tiptap WYSIWYG 에디터 공통 컴포넌트
    - v-model: HTML 문자열 (서버 측 OWASP HTML Sanitizer가 XSS 방어 담당)
    - 키보드 단축키: Tiptap 표준 (Ctrl+B, Ctrl+I, Ctrl+U, Ctrl+Shift+X 등)
    - 접근성: 모든 툴바 버튼 Korean aria-label, WCAG 4.5:1 포커스 인디케이터
  -->
  <div class="tiptap-editor" :aria-label="ariaLabel">
    <!-- 툴바 -->
    <div v-if="editor" class="tiptap-toolbar" role="toolbar" aria-label="에디터 도구 모음">
      <!-- 인라인 서식 -->
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('bold') }"
        aria-label="굵게"
        title="굵게 (Ctrl+B)"
        @click="editor.chain().focus().toggleBold().run()"
      >
        <strong>B</strong>
      </button>
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('italic') }"
        aria-label="기울임"
        title="기울임 (Ctrl+I)"
        @click="editor.chain().focus().toggleItalic().run()"
      >
        <em>I</em>
      </button>
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('underline') }"
        aria-label="밑줄"
        title="밑줄 (Ctrl+U)"
        @click="editor.chain().focus().toggleUnderline().run()"
      >
        <span style="text-decoration: underline">U</span>
      </button>
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('strike') }"
        aria-label="취소선"
        title="취소선 (Ctrl+Shift+X)"
        @click="editor.chain().focus().toggleStrike().run()"
      >
        <s>S</s>
      </button>

      <span class="tiptap-toolbar__divider" aria-hidden="true"></span>

      <!-- 헤딩 -->
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('heading', { level: 1 }) }"
        aria-label="제목 1"
        title="제목 1"
        @click="editor.chain().focus().toggleHeading({ level: 1 }).run()"
      >
        H1
      </button>
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('heading', { level: 2 }) }"
        aria-label="제목 2"
        title="제목 2"
        @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
      >
        H2
      </button>
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('heading', { level: 3 }) }"
        aria-label="제목 3"
        title="제목 3"
        @click="editor.chain().focus().toggleHeading({ level: 3 }).run()"
      >
        H3
      </button>

      <span class="tiptap-toolbar__divider" aria-hidden="true"></span>

      <!-- 블록 -->
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('blockquote') }"
        aria-label="인용구"
        title="인용구"
        @click="editor.chain().focus().toggleBlockquote().run()"
      >
        &ldquo;&nbsp;&rdquo;
      </button>
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('codeBlock') }"
        aria-label="코드 블록"
        title="코드 블록"
        @click="editor.chain().focus().toggleCodeBlock().run()"
      >
        &lt;/&gt;
      </button>

      <span class="tiptap-toolbar__divider" aria-hidden="true"></span>

      <!-- 리스트 -->
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('bulletList') }"
        aria-label="번호 없는 목록"
        title="번호 없는 목록"
        @click="editor.chain().focus().toggleBulletList().run()"
      >
        &bull;
      </button>
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('orderedList') }"
        aria-label="번호 목록"
        title="번호 목록"
        @click="editor.chain().focus().toggleOrderedList().run()"
      >
        1.
      </button>

      <span class="tiptap-toolbar__divider" aria-hidden="true"></span>

      <!-- 링크 -->
      <button
        type="button"
        class="tiptap-toolbar__btn"
        :class="{ 'is-active': editor.isActive('link') }"
        aria-label="링크 삽입"
        title="링크 삽입"
        @click="handleSetLink"
      >
        🔗
      </button>

      <!-- 이미지 업로드 (uploadImage prop 제공 시 노출) -->
      <template v-if="uploadImage">
        <span class="tiptap-toolbar__divider" aria-hidden="true"></span>
        <button
          type="button"
          class="tiptap-toolbar__btn"
          aria-label="이미지 삽입"
          title="이미지 업로드"
          :disabled="imageUploading"
          @click="triggerImageUpload"
        >
          {{ imageUploading ? '⏳' : '🖼' }}
        </button>
        <input
          ref="imageInputRef"
          type="file"
          accept="image/*"
          class="sr-only"
          aria-hidden="true"
          @change="handleImageFile"
        />
      </template>

      <span class="tiptap-toolbar__divider" aria-hidden="true"></span>

      <!-- 실행 취소/다시 실행 -->
      <button
        type="button"
        class="tiptap-toolbar__btn"
        aria-label="실행 취소"
        title="실행 취소 (Ctrl+Z)"
        :disabled="!editor.can().undo()"
        @click="editor.chain().focus().undo().run()"
      >
        ↶
      </button>
      <button
        type="button"
        class="tiptap-toolbar__btn"
        aria-label="다시 실행"
        title="다시 실행 (Ctrl+Shift+Z)"
        :disabled="!editor.can().redo()"
        @click="editor.chain().focus().redo().run()"
      >
        ↷
      </button>
    </div>

    <!-- 에디터 본문 -->
    <EditorContent
      :editor="editor"
      class="tiptap-editor__content"
      :style="contentStyle"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableHeader from '@tiptap/extension-table-header'
import TableCell from '@tiptap/extension-table-cell'

// @MX:NOTE: SPEC-CMS-003 — Tiptap WYSIWYG 에디터 공통 컴포넌트; uploadImage prop으로 이미지 업로드 연동
const imageInputRef = ref<HTMLInputElement | null>(null)
const imageUploading = ref(false)
// 서버 측 OWASP HTML Sanitizer가 XSS 방어 담당, 클라이언트는 sanitize 미적용

interface Props {
  modelValue: string
  placeholder?: string
  rows?: number
  ariaLabel?: string
  uploadImage?: (file: File) => Promise<string>
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '',
  rows: 10,
  ariaLabel: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

// 최소 높이: rows * 1.5rem + padding (위아래 12px = 24px 총합)
const contentStyle = computed(() => ({
  minHeight: `calc(${props.rows} * 1.5rem + 24px)`,
}))

// @MX:ANCHOR: Tiptap 에디터 인스턴스 — v-model 양방향 바인딩
// @MX:REASON: v-model HTML 문자열과 Tiptap 내부 상태 동기화는 모든 호출 지점에서 신뢰해야 하는 invariant
const editor = useEditor({
  content: props.modelValue,
  extensions: [
    // StarterKit v3은 Underline/Link 포함, 명시적 옵션으로 덮어쓰기 위해 비활성화
    StarterKit.configure({
      underline: false,
      link: false,
    }),
    Underline,
    Link.configure({
      openOnClick: false,
      HTMLAttributes: {
        rel: 'noopener noreferrer',
        target: '_blank',
      },
    }),
    Image,
    Table.configure({
      resizable: true,
    }),
    TableRow,
    TableHeader,
    TableCell,
  ],
  editorProps: {
    attributes: {
      // ProseMirror 컨테이너 자체 aria-label (스크린리더 인식)
      'aria-label': props.ariaLabel || '에디터 본문',
      'aria-multiline': 'true',
      'aria-placeholder': props.placeholder,
      role: 'textbox',
    },
  },
  onUpdate: ({ editor: ed }) => {
    const html = ed.getHTML()
    emit('update:modelValue', html)
  },
})

// 외부에서 modelValue 변경 시 에디터 내용 동기화 (무한 루프 방지)
watch(
  () => props.modelValue,
  (newValue) => {
    const ed = editor.value
    if (!ed) return
    const current = ed.getHTML()
    if (newValue !== current) {
      ed.commands.setContent(newValue || '', { emitUpdate: false })
    }
  },
)

// 링크 삽입 처리 — window.prompt로 URL 입력받음
function handleSetLink(): void {
  const ed = editor.value
  if (!ed) return

  const previousUrl = (ed.getAttributes('link').href as string) || ''
  const url = window.prompt('링크 URL을 입력하세요:', previousUrl)

  // 취소 시 변경 없음
  if (url === null) return

  // 빈 문자열 입력 시 링크 제거
  if (url === '') {
    ed.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }

  ed.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
}

function triggerImageUpload(): void {
  imageInputRef.value?.click()
}

async function handleImageFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !props.uploadImage) return

  imageUploading.value = true
  try {
    const url = await props.uploadImage(file)
    editor.value?.chain().focus().setImage({ src: url, alt: file.name }).run()
  } catch {
    // 업로드 실패 — 조용히 처리 (호출자에서 에러 표시)
  } finally {
    imageUploading.value = false
    // 같은 파일 재선택 허용
    if (input) input.value = ''
  }
}

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<style scoped>
.tiptap-editor {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: white;
  display: flex;
  flex-direction: column;
}

.tiptap-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  border-top-left-radius: 4px;
  border-top-right-radius: 4px;
}

.tiptap-toolbar__btn {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  font-size: 14px;
  color: #303133;
  background: white;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.tiptap-toolbar__btn:hover:not(:disabled) {
  background: #ecf5ff;
  border-color: #c6e2ff;
  color: #409eff;
}

.tiptap-toolbar__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tiptap-toolbar__btn.is-active {
  background: #ecf5ff;
  border-color: #1a73e8;
  color: #1a73e8;
}

/* WCAG 2.1 AA — 4.5:1 contrast 보장하는 포커스 인디케이터 */
.tiptap-toolbar__btn:focus-visible {
  outline: 2px solid #1a73e8;
  outline-offset: 2px;
  z-index: 1;
}

.tiptap-toolbar__divider {
  width: 1px;
  height: 20px;
  background: #dcdfe6;
  margin: 0 4px;
}

.tiptap-editor__content {
  flex: 1;
  overflow-y: auto;
}

/* ProseMirror 콘텐츠 영역 — :deep()으로 scoped 안에서 내부 노드 스타일링 */
.tiptap-editor__content :deep(.ProseMirror) {
  padding: 12px;
  outline: none;
  min-height: inherit;
  line-height: 1.6;
  color: #303133;
}

/* WCAG 2.1 AA — 4.5:1 contrast 포커스 인디케이터 (Element Plus blue #1a73e8) */
.tiptap-editor__content :deep(.ProseMirror:focus) {
  outline: 2px solid #1a73e8;
  outline-offset: -2px;
  border-radius: 3px;
}

/* 콘텐츠 노드 스타일 */
.tiptap-editor__content :deep(.ProseMirror h1) {
  font-size: 1.75rem;
  font-weight: 600;
  margin: 0.75rem 0 0.5rem;
}
.tiptap-editor__content :deep(.ProseMirror h2) {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0.75rem 0 0.5rem;
}
.tiptap-editor__content :deep(.ProseMirror h3) {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0.5rem 0;
}
.tiptap-editor__content :deep(.ProseMirror p) {
  margin: 0.5rem 0;
}
.tiptap-editor__content :deep(.ProseMirror blockquote) {
  border-left: 3px solid #dcdfe6;
  padding-left: 12px;
  margin: 0.5rem 0;
  color: #606266;
}
.tiptap-editor__content :deep(.ProseMirror pre) {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
  font-family: 'Courier New', monospace;
  font-size: 0.875rem;
  overflow-x: auto;
}
.tiptap-editor__content :deep(.ProseMirror code) {
  background: #f5f7fa;
  padding: 2px 4px;
  border-radius: 2px;
  font-family: 'Courier New', monospace;
  font-size: 0.875rem;
}
.tiptap-editor__content :deep(.ProseMirror ul),
.tiptap-editor__content :deep(.ProseMirror ol) {
  padding-left: 1.5rem;
  margin: 0.5rem 0;
}
.tiptap-editor__content :deep(.ProseMirror a) {
  color: #1a73e8;
  text-decoration: underline;
}
.tiptap-editor__content :deep(.ProseMirror img) {
  max-width: 100%;
  height: auto;
}
.tiptap-editor__content :deep(.ProseMirror table) {
  border-collapse: collapse;
  margin: 0.5rem 0;
  width: 100%;
}
.tiptap-editor__content :deep(.ProseMirror table th),
.tiptap-editor__content :deep(.ProseMirror table td) {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  min-width: 60px;
}
.tiptap-editor__content :deep(.ProseMirror table th) {
  background: #f5f7fa;
  font-weight: 600;
}
</style>
