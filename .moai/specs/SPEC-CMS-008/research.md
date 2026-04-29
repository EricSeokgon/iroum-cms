---
id: SPEC-CMS-008
title: 시각화 대시보드 + KPI 통합 — 기술 리서치
version: 0.1.0
status: Draft
created: 2026-04-29
parent_spec: SPEC-CMS-001 v0.3.2
sections: 8
---

# SPEC-CMS-008 Research

본 문서는 SPEC-CMS-008 의 핵심 기술 결정에 대한 비교 분석과 권장안을 정리한다. 모든 결정은 SPEC-CMS-001 v0.3.2 §15.2 SFR-009/013, INR-001~012, KWCAG 2.2 AA 제약 + tech.md FROZEN 스택(Vue 3.5 + Element Plus + Spring Boot 3.2 + PostgreSQL 16) 을 전제로 한다.

---

## 1. 차트 라이브러리 선정

### 1.1 후보 비교

| 라이브러리 | 라이선스 | 한국어 | 방사형 | 매트릭스 히트맵 | 대한민국 지도 | 번들 크기 | 접근성 |
|---|---|---|---|---|---|---|---|
| **ECharts 5** | Apache 2.0 | 우수 | 기본 | 기본 | registerMap GeoJSON | ~270KB (tree-shake 후 ~180KB) | aria 옵션 내장 |
| Highcharts 11 | 상용 (비상업적 무료) | 우수 | 기본 | 기본 | Highmaps 별도 모듈 | ~140KB | 우수 (a11y 모듈) |
| Chart.js 4 | MIT | 보통 | 기본 (단순) | 플러그인 | 별도 (chartjs-chart-geo) | ~70KB | 보통 |
| Recharts 2 | MIT | React 전용 | 기본 | 플러그인 | 미지원 | ~120KB | 보통 |
| ApexCharts | MIT | 보통 | 기본 | 기본 | 미지원 | ~150KB | 보통 |

### 1.2 권장: ECharts 5

선정 사유:
1. **라이선스**: Apache 2.0 → RFP INR-010 "추가 라이선스 비용 없음" 100% 만족. Highcharts 는 상업 사용 시 라이선스 부담 (연간 $535/dev~)
2. **한국어 + 지도**: 행정안전부 표준 GeoJSON 호환, 한국어 라벨 포맷 우수
3. **차트 다양성**: 9 widget_type 중 8개를 단일 라이브러리로 커버 (METRIC_CARD, TABLE 만 별도)
4. **접근성**: ECharts 5 의 `aria.show: true` 옵션으로 자동 ARIA description 생성, 별도 플러그인 불필요
5. **Vue 3 통합**: vue-echarts 7.x 가 Vue 3 Composition API 완전 지원, SFC 친화

리스크 및 대응:
- **번들 크기**: tree-shaking 으로 BarChart/LineChart/RadarChart/HeatmapChart/MapChart 만 import 시 ~180KB. 추가로 vite chunk splitting 으로 첫 LCP 영향 최소화
- **3D 차트 미지원 (echarts-gl 필요)**: 1차 범위에 3D 없음. 옵션 트랙으로 분리

### 1.3 거부된 후보

- Highcharts: 라이선스 비용 + 가격 정책 복잡 (CMS 내부 사용도 enterprise 라이선스 필요할 수 있음)
- Chart.js: 매트릭스 히트맵·지도 플러그인 의존, 방사형 단순 형태만 지원
- Recharts: React 전용, Vue 와 호환 어려움
- ApexCharts: 한국 지도 미지원, KWCAG 적합성 검증 자료 부족

---

## 2. 차트 접근성 (KWCAG 2.2 AA)

### 2.1 ECharts ARIA 옵션

ECharts 5 는 `aria.show: true` 활성 시 자동으로 다음을 생성:
- `aria-label`: 차트 제목 + 시리즈 요약 (예: "BAR_CHART, 페이지뷰 by 기능, 7개 카테고리")
- `aria-roledescription`: "chart"
- 시리즈별 `aria-description`: 각 데이터 포인트 (카테고리, 값, 단위)

### 2.2 보강: 데이터 테이블 fallback

ARIA 만으로는 스크린리더가 차트 모양을 음성화하기 어렵기 때문에, 차트 하단에 `<details><summary>표 형식 데이터</summary><table>...</table></details>` 를 자동 생성:
- 위젯 컴포넌트가 dataset 을 표 형식으로 변환
- 기본 접힘, 키보드 Tab 으로 펼침 가능
- 스크린리더는 표를 행/열 단위로 정확히 읽을 수 있음

### 2.3 색상 외 정보

KWCAG 1.4.1 준수: 색상 단독으로 정보 전달 금지.
- 시리즈별 패턴(점선/실선/해치/점) 적용 — ECharts `lineStyle.type` + `itemStyle.decal`
- 시리즈 라벨 텍스트 동시 표시 (label.show: true)
- 흑백 인쇄 시 패턴으로만 식별 가능해야 함

### 2.4 권장 결론

ECharts `aria.show: true` + 자동 데이터 테이블 fallback + 패턴/라벨 동시 표시 3중 보강으로 KWCAG 2.2 AA 충족.

---

## 3. 대시보드 레이아웃 라이브러리

### 3.1 후보 비교

| 라이브러리 | Vue 3 호환 | 12-grid | 드래그·리사이즈 | 모바일 반응형 | 활성 유지보수 |
|---|---|---|---|---|---|
| **vue-grid-layout (next 버전)** | Vue 3 호환 (npm `vue-grid-layout-v3`) | 기본 | 기본 | media breakpoint | 활발 (2026 기준) |
| gridstack 10 | Vue 미공식 (Web Component 또는 wrapper 필요) | 기본 | 기본 | 기본 | 매우 활발 |
| muuri 0.9 | wrapper 필요 | flexbox | 기본 | 기본 | 보통 |
| 자체 구현 (CSS Grid + interact.js) | 완전 호환 | 자유 | 별도 구현 | 완전 자유 | N/A |

### 3.2 권장: vue-grid-layout-v3

선정 사유:
- Vue 3 SFC 친화적 API (`<grid-layout :layout :col-num=12>`)
- `position(x,y,w,h)` 가 `dashboard_layout_widget.position` JSONB 와 직접 매핑 가능
- 미디어 breakpoint 기본 지원 (모바일 전환 시 1-column 자동 폴백)
- 드래그·리사이즈 이벤트 → 즉시 PUT `/layouts/{id}` 동기화 가능

리스크:
- gridstack 보다 커뮤니티 작음 → 향후 Vue 4 마이그레이션 시 fork 또는 자체 구현 가능성 (가능성 낮음)

대응: 핵심 동작(겹침 검증, 직렬화) 은 utils 로 분리, 라이브러리 교체 시 리팩토링 비용 최소화.

---

## 4. 엑셀 스트리밍 라이브러리 (백엔드)

### 4.1 후보 비교

| 라이브러리 | 언어 | 메모리 효율 | 100만 행 | 라이선스 | 비고 |
|---|---|---|---|---|---|
| **Apache POI SXSSF** | Java | 매우 우수 (window=100) | < 100MB heap | Apache 2.0 | tech.md Spring Boot 호환 |
| Apache POI XSSF | Java | 나쁨 (전체 메모리) | OOM | Apache 2.0 | 작은 파일용 |
| FastExcel (xlsxwriter Java) | Java | 우수 (streaming) | < 80MB | BSD 2-clause | 신규, 커뮤니티 작음 |
| EasyExcel (Alibaba) | Java | 우수 | < 100MB | Apache 2.0 | 중국어 문서 위주 |
| openpyxl write_only | Python | 우수 | < 100MB | MIT | 백엔드 언어 미스매치 |

### 4.2 권장: Apache POI SXSSFWorkbook (window=100)

선정 사유:
- tech.md FROZEN: Spring Boot 3.2 (Java 17), POI 가 사실상 표준
- SXSSF: 메모리에 N 행만 보유, 초과 시 디스크 임시 파일에 flush. window=100 이 100만 행에서 ~30MB heap 검증
- 풍부한 문서, Stack Overflow 사례 다수, 운영팀 학습 곡선 낮음
- SPEC-CMS-005 v0.2.1 §13.1 REQ-SYSTEM-007-D-4 와 동일 기술 — 일관성 확보

### 4.3 청크 처리 패턴

```
1. 컨트롤러: count(*) 추정 → 10000 초과 시 비동기 등록
2. 비동기 워커:
   - SXSSFWorkbook(100) 생성
   - PreparedStatement.setFetchSize(1000) → ResultSet streaming
   - 1000 행 페치마다 sheet.createRow + Cell write
   - SXSSFSheet.flushRows(100) 자동 호출 (디스크 flush)
   - 5% 단위로 export_history.progress_pct UPDATE
3. 완료: workbook.write(FileOutputStream) → workbook.dispose() (임시 파일 정리)
```

리스크: 디스크 임시 파일 누수 → finally 블록에서 dispose 호출 + 30분 cleanup batch 보강.

---

## 5. 캐싱 전략

### 5.1 후보 비교

| 캐시 | 타입 | 단일 노드 | 멀티 노드 | TTL 정확도 | 구현 비용 |
|---|---|---|---|---|---|
| **Caffeine** | 인메모리 (JVM heap) | 우수 | 미지원 (노드 간 비공유) | 우수 | 매우 낮음 (Spring Cache Abstraction 통합) |
| Redis 7 | 외부 캐시 | 우수 | 우수 | 우수 | 중 (Redis 인프라 운영 추가) |
| EhCache 3 | 인메모리 + 디스크 | 우수 | terracotta 필요 | 우수 | 중 |
| DB-only (chart_dataset_cache) | 영속 | OK | OK | OK | 낮음 (테이블 1개) |

### 5.2 권장: 1차 Caffeine + DB 보조, 멀티 노드 도입 시 Redis 전환

설계:
- **L1 캐시**: Caffeine (in-process, 5분 TTL, max 1000 entries) — 가장 빠른 hit
- **L2 캐시**: `chart_dataset_cache` 테이블 (DB 영속) — 노드 재시작 후 warm-up, 다른 노드 공유 (DB 가 동기화 매개)
- 단일 노드 운영 단계: L1 + L2 (DB) 충분
- 멀티 노드 (k8s scale-out) 도입 시: L1 → Redis 전환, L2 보존

이유:
- iroum-cms 는 1차 출시 단일 노드 가정 (RFP 명시 없음, 운영 단순화). Redis 인프라 도입을 미루고 단순한 Caffeine + DB 로 시작
- DB 캐시 테이블은 운영 가시성 확보 (어떤 cache_key 가 자주 hit/miss 되는지 SQL 로 조회 가능)
- 멀티 노드 전환 시점에 Redis 도입 (research.md §5 재방문)

### 5.3 캐시 키 설계

`widget:{widget_id}:dim:{period_hash}:role:{role_code}` 형식.
- period_hash: SHA1(filter_state JSON canonical) 첫 8자
- role_code 포함: DEPT_ADMIN A 부서 데이터가 B 부서로 누출 방지

무효화: 위젯·KPI 수정 시 prefix scan (`widget:{id}:*`) → DELETE.

---

## 6. 필터 URL 동기화 패턴

### 6.1 후보 비교

| 방식 | 설명 | 장점 | 단점 |
|---|---|---|---|
| **Vue Router query + Pinia derived** | URL 이 진실, store 는 derived | SSR/북마크 호환, 단일 진실 | router.push 호출 명시적 |
| Pinia 전용 | store 가 진실, URL 무시 | 코드 단순 | 새로고침·공유 시 상태 소실 |
| LocalStorage | 브라우저 저장 | 영속 | 디바이스 간 불일치, 공유 불가 |
| URL fragment (#) | hash 만 사용 | SSR 부담 없음 | 서버 로깅 불가 (fragment 는 서버에 안 감) |

### 6.2 권장: Vue Router query + Pinia derived

설계:
- Vue Router `route.query` 가 단일 진실 (single source of truth)
- 필터 변경 시 `router.push({query: {...newFilter}})` 호출 → 자동으로 URL 갱신
- Pinia store 는 `route.query` 를 watch 하여 derived state 로 노출
- 위젯 컴포넌트는 store 만 의존 → URL 형식 변경 시 store 만 수정

장점:
- 새로고침·뒤로가기·공유 링크 모두 동일 상태 복원
- SSR 친화 (Vite SSR 시 query parse 가 서버에서도 동작)
- RFP INR-009 "Cross Browser" 호환 (모든 브라우저 query string 표준)

### 6.3 직렬화 형식

복잡 객체 (multi-select 배열) 처리:
- `feature=board,policy` (comma-separated)
- `industry=561220,562910` (KSIC 코드)
- 특수문자는 percent-encoding (예: 콤마 자체가 값에 포함될 경우 `%2C`)

---

## 7. 사용자 정의 쿼리 보안

### 7.1 후보 비교

| 방식 | 보안 | 유연성 | 운영 부담 |
|---|---|---|---|
| **Whitelist Template** | 매우 우수 | 중 (사전 등록 필요) | 낮음 |
| 동적 SQL builder (예: jOOQ) | 우수 (구문 강제) | 우수 | 중 |
| Stored Procedure | 우수 | 낮음 (DB 의존) | 높음 (DB 마이그레이션) |
| Free-text SQL + parser | 위험 | 우수 | 매우 높음 (parser 유지보수) |

### 7.2 권장: Whitelist Template (사전 등록 + 파라미터 바인딩)

설계:
- 운영팀이 SQL 템플릿을 사전에 코드 또는 DB 시드로 등록 (예: `top_downloads_v1`, `policy_apply_funnel_v1`)
- 위젯 등록 시 `data_source_config = {"query_template_id": "top_downloads_v1", "params": {"limit": 10}}`
- 백엔드는 template_id 로 SQL 조회 → PreparedStatement 로 params 바인딩 → 실행
- INSERT/UPDATE/DELETE/DDL 토큰은 SQL 등록 시점에 정규식 검증 + DB CHECK constraint 로 거부

장점:
- SQL Injection 원천 차단 (사용자 입력은 params 만, params 는 PreparedStatement 바인딩)
- 운영팀이 새 템플릿 추가하려면 PR 거쳐야 하므로 검토 가능
- 성능 이슈 SQL 도 사전에 EXPLAIN 검증 가능

리스크: 새 분석 요구 시 PR 필요 → 운영 속도 저하. 대응: `dashboard_widget` 메타에 `kpi_id` 직접 매핑하면 즉시 사용 가능, 화이트리스트는 KPI 외 보조 분석에만 사용.

---

## 8. 모바일 반응형 전략

### 8.1 후보 비교

| 전략 | 장점 | 단점 |
|---|---|---|
| **CSS Grid 반응형 + ECharts auto-resize** | 단일 코드, 유지보수 단순 | 모바일 UX 최적화 한계 |
| 별도 모바일 라우트 (/m/...) | 모바일 UX 최적화 | 코드 중복, 유지보수 부담 2배 |
| User-Agent 분기 SSR | 서버에서 분기 | 캐시 복잡, SEO 취약 |
| PWA + 동일 코드 | 오프라인 지원 추가 | service worker 운영 부담 |

### 8.2 권장: CSS Grid + ECharts auto-resize

설계:
- vue-grid-layout 의 `responsive: true` + media breakpoint:
  - `xxs (< 480px)`: 1 column
  - `xs (480~768)`: 2 columns
  - `sm (768~996)`: 6 columns
  - `md (996~1200)`: 10 columns
  - `lg (>= 1200)`: 12 columns
- ECharts 의 `media` 옵션: 화면 크기별 시리즈 옵션 차등 (모바일에서는 범례를 하단으로 이동, 폰트 12px)
- ResizeObserver: 컨테이너 변경 감지 → 즉시 ECharts.resize() 호출

이유:
- RFP INR-003/004 "PC/모바일/Tablet 지원, 메인 시안 3종" 충족
- INR-005 "수평 스크롤 지양" 충족 (모바일에서 1-column 자동 폴백)
- 단일 코드베이스로 유지보수 부담 최소

### 8.3 리스크 및 대응

- 매트릭스 히트맵·대한민국 지도가 모바일에서 너무 작아 가독성 저하 → "확대 모드" 버튼 제공 (전체 화면 토글)
- 폰트 크기 12px 미만 사용 금지 (KWCAG 1.4.4 텍스트 크기 200% 확대 호환)

---

## 부록 A. 의존성 버전 핀 (잠정)

| 라이브러리 | 버전 | 비고 |
|---|---|---|
| echarts | ^5.5.0 | tree-shaking via vite |
| vue-echarts | ^7.0.3 | Vue 3 wrapper |
| vue-grid-layout-v3 | ^1.0.5 | Vue 3 fork |
| @vueuse/core | ^11.0.0 | ResizeObserver, useRouteQuery |
| element-plus | ^2.8.0 | Existing (tech.md) |
| apache.poi | 5.3.0 | SXSSFWorkbook |
| apache.poi-ooxml | 5.3.0 | xlsx 포맷 |
| spring-boot-starter-cache | 3.2.x | Caffeine |
| caffeine | ^3.1.8 | Spring Cache provider |

부록 B. 미해결 질문 (annotation cycle 에서 결정 필요)

1. **모바일 폴링 주기**: 데스크톱 30초 vs 모바일 60초 차등? 배터리 영향 검증 필요
2. **위젯 시드 세트**: 첫 출시 기본 대시보드에 어떤 위젯 8~10개를 시드로 포함할지? (KPI 8 종 매핑 + 안전경영 + 정책매칭)
3. **export 파일 저장소**: 로컬 FS vs S3 호환 객체 스토리지? RFP §15.8 "통합파일서버 비범위" 와 정합 검토 필요
