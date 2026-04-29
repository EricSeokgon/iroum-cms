# RFP 기반 CMS 기능 갭 분석 보고서

문서 버전: v0.2 (2026-04-29 사용자 결정 반영)
작성일: 2026-04-29
대상 SPEC 비교군: SPEC-CMS-001 ~ SPEC-CMS-005 (현재 .moai/specs/ 하위)
입력 자료: 사용자가 제공한 "CMS 개발 관점 요구사항 검토" 문서 + RFP 원문 (`.moai/refs/RFP/비즈패스파인더 고도화 용역_제안요청서.pdf`, 53페이지)
관련 문서: `.moai/refs/rfp-summary.md` (RFP 요구사항 카드 v0.1)
사용 목적: 기존 SPEC-CMS-001~005를 기준으로 RFP **기능 요구사항만 추가/보강** (기술 스택은 처음 제시안 유지)

## v0.2 변경 사항 (2026-04-29 사용자 결정 반영)

이전 v0.1은 "전면 재작성"을 가정했으나, 사용자 최종 결정으로 다음과 같이 변경:

- **기술 스택**: 처음 제시한 그대로 유지 — Vue 3.5+ + TypeScript + Vite + Spring Boot 3.2.x + Java 17 + egovFrame v5.0.0 + PostgreSQL 16 + JWT + Docker + KWCAG 2.2
- **RFP 활용**: **기능 요구사항만** 추출하여 기존 SPEC에 amendment / 신규 SPEC 추가
- **archive 이동**: 기존 SPEC-CMS-001~005 폐기 안 함, 그대로 유지·보강
- **AI/ML 트랙**: Milvus + RAG는 별도 옵션 SPEC으로 추가 (선택)
- **SSO**: 상급기관 SSO는 별도 옵션 SPEC으로 추가 (선택)

---

## 0. 핵심 결론 (Executive Summary)

기존 SPEC-CMS-001~005는 RFP와 **다음 5개 축에서 정합하지 않음**:

1. **기술 스택**: JDK 17 / Spring Boot 3.2 / PostgreSQL / Vue 3 SPA
   → RFP는 JDK 8/11 / Spring Boot 2.x / MariaDB+Milvus / JSP 유지
2. **인증**: 자체 JWT
   → RFP는 상급기관 SSO (SAML 2.0 또는 OIDC) — SFR-010
3. **누락 도메인 11개**: 안전경영, 정책사업, 사고사례, 알림톡, 설문, 통계지표, 시각화 대시보드, 데이터 거버넌스, 알고리즘 모니터링, KPI 대시보드, 콘텐츠 마이그레이션
4. **AI/ML 트랙 부재**: Milvus + RAG + 정책 매칭 + SFR-012 모니터링
5. **이관 요건 부재**: 기존 비즈패스파인더 → 통합 홈페이지 (SFR-010)

→ **전면 재작성 결정**(2026-04-29 사용자 승인). 본 보고서는 재작성 시 갭 매핑 입력 자료.

---

## 1. 콘텐츠 영역 갭 (RFP 1-1)

| RFP 모듈 | 근거 | 현재 SPEC 커버 | 갭 / 추가 필요 | 우선순위 |
|---|---|---|---|---:|
| 다중 게시판 (유형별 템플릿) | 기존 + SFR-014 | △ SPEC-003 단일 bbs_master만 | 게시판 유형 5~7개(일반/공지/Q&A/FAQ/갤러리/자료실/설문)별 템플릿 분리 + 카테고리/태그/비밀글/RSS | P0 |
| 공지사항 | 기존 | △ SPEC-003 type='NOTICE' | 상단고정·노출기간·중요도(긴급/일반)·발송연동 | P0 |
| 발간자료(자료실) | 기존 | △ 첨부 모델만 | 카테고리·발간연도·다운로드 통계·압축다운로드 | P1 |
| 설문조사 | 기존 | 미정의 | 신규: 설문 마스터/문항/응답 테이블, 통계 시각화 | P1 |
| 안전경영 가이드라인 템플릿 | SFR-006 | 미정의 | 신규: 가이드라인 마스터·체크리스트·체크 결과 추적 | P0 |
| 정책사업 콘텐츠 | SFR-007 | 미정의 | 신규: 정책마스터·자격요건·공고기간·매칭조건(업종/지역/매출/업력) | P0 |
| 통계/지표 콘텐츠 (지역/업종/업력) | 기존 | 미정의 | 신규: 지표 데이터 모델 + 시각화 차트(SFR-009 연계) | P0 |
| 경영지표·경제지표 콘텐츠 | 기존 | 미정의 | 신규: 외부 지표 인입(API/CSV) + 시각화 | P1 |
| 알림톡/메일 템플릿 | SFR-008 | 미정의 | 신규: 템플릿 마스터·승인이력·변수 치환·미리보기 | P0 |
| 사고 사례 콘텐츠 | SFR-004 | 미정의 | 신규: 사고유형·발생일·요약·교훈·태그·검색 | P0 |

콘텐츠 영역 신규 SPEC 후보: 7~8개

---

## 2. 운영·관리 영역 갭 (RFP 1-2)

| RFP 모듈 | 근거 | 현재 SPEC 커버 | 갭 / 추가 필요 | 우선순위 |
|---|---|---|---|---:|
| 메뉴 관리(트리) | SFR-014, 기존 | SPEC-004 menu (Adjacency+MaterializedPath) | 발행 예약/만료, 메뉴별 SEO 메타 — 유지 | P0 |
| Role/권한 관리(RBAC) | SFR-014 | △ SPEC-002 단순 RBAC | 4단계(Super/부서Admin/Editor/Viewer) + 역할 템플릿 + 메뉴×역할×액션 매트릭스 | P0 |
| 조직·부서 관리 | 기존 + SFR-014 | 없음 | 신규: organization 트리·user.dept_id·부서별 콘텐츠 격리 | P0 |
| 사용자 관리 + SSO 매핑 | SFR-010 | 자체 인증만 | 상급기관 SSO (SAML 2.0/OIDC) + 매핑 테이블 + 휴면/탈퇴/중복 룰 | P0 |
| 접근 IP 관리 | 기존 | △ SPEC-002 §9.4 옵션 | 관리자 영역 화이트리스트 강제 | P0 |
| 사용자 잠금 정보 | 기존 | SPEC-002 잠금 모델 | 관리자 일괄 해제 화면 추가 | P1 |
| 권한 변경 이력 로그 | SFR-014 | △ SPEC-005 일반 적재 | 권한 변경 전용 뷰·검색 + 비인가자 사전 차단 | P0 |
| 배치 모니터링 | 기존 | 없음 | 신규: 배치 작업 등록·실행이력·실패 알림 | P1 |
| 시스템 로그(접속/SSO/연계) | SFR-015 | △ SPEC-005 access_log만 | SSO 인증로그·외부연계 호출로그(상급기관/카카오/지표API) 분리 | P0 |
| 알고리즘 품질 모니터링 | SFR-012 | 없음 (AI 영역) | 신규 AI SPEC: 매칭 정확도·드리프트·재학습 트리거 | P0 |
| KPI 통합 대시보드 | SFR-013 | △ SPEC-005 부분 | 매칭전환율·알림도달/클릭·체류시간·기간/기능/업종 필터·Excel 스트리밍 다운로드 | P0 |
| 데이터 거버넌스 콘솔 | SFR-011 | 없음 | 신규: S-Meta/DA# 등록·코드사전(지역/업종/사업유형)·표준변경 영향분석 | P0 |

운영·관리 영역 신규 SPEC 후보: 6~7개

---

## 3. 사용자 인터페이스 갭 (RFP 1-3)

| RFP 모듈 | 근거 | 현재 SPEC 커버 | 갭 / 추가 필요 | 우선순위 |
|---|---|---|---|---:|
| 시각화 대시보드(방사형/매트릭스) | SFR-009 | 없음 | 신규: ECharts/Highcharts 기반 위젯·디자인 시안 의존(critical path) | P0 |
| 통합 검색 + 자동완성 | SFR-009, INR | △ SPEC-003 PG FTS만 | 별도 검색엔진 (Elasticsearch/OpenSearch + nori/mecab-ko) + 자동완성 + 인기검색어 | P0 |
| 반응형 (PC/태블릿/모바일) | INR-001/009 | △ Vue SPA로만 가정 | JSP + 반응형 CSS (eGovFrame 표준 호환), 모바일 우선 | P0 |
| 웹접근성 KWCAG 2.2 | COR | 모든 SPEC에 명시 | 유지 + 자동검사도구(aXe/WAVE) + 분기별 수동감사 | P0 |
| 다국어 | 기존 검토필요 | SPEC-004 i18n_resource | 기존 비즈패스파인더 다국어 여부 확인 후 결정 | P2 |

---

## 4. 핵심 기술 요소 변경 항목 (RFP 2-1 ~ 2-14)

| 항목 | RFP 요구 | 내 SPEC 가정 | 변경 필요 |
|---|---|---|---|
| eGovFrame | COR-001 명시 | 5.0.0 가정 | JDK 8/11 호환 버전(3.10/4.x)으로 다운그레이드 |
| JDK | 8/11 | 17 | 8 또는 11로 변경 |
| Spring Boot | (eGov 호환) | 3.2.x | 2.x로 변경 (Jakarta EE 미지원, javax.* 패키지) |
| Frontend | JSP + Java + Python 유지 | Vue 3 SPA | JSP 기반으로 전환 (관리자만 SPA 옵션 검토) |
| DB | MariaDB + Milvus 듀얼 | PostgreSQL 16 | MariaDB 10.x + Milvus 2.x로 변경 |
| WAS | Apache + Tomcat | 내장 Tomcat 10 | 외부 Tomcat 9 + Apache reverse proxy |
| 캐시 | Redis 권장 | Caffeine만 | Redis 추가 |
| 메시지 큐 | (필요) | 없음 | Redis Stream 또는 RabbitMQ 결정 |
| 인증 | SAML 2.0 또는 OIDC SSO | 자체 JWT | SSO 기반으로 재설계, JWT는 내부 세션용 |
| WYSIWYG | (필수) | Tiptap 권장 | CKEditor 5 또는 Toast UI Editor 한국어 우선 검토 |
| 검색엔진 | (대규모면 필요) | PG FTS | Elasticsearch/OpenSearch + nori 도입 |
| 시각화 | ECharts/Highcharts | 미정 | 라이선스·운영 정책 결정 |
| 첨부 저장 | 통합파일서버 SER-006 | Local FS | 공용 NAS/객체스토리지 + 다운로드 권한 검증 |
| 워크플로 | 표준 권고 | 없음 | 작성→검토→승인 도입 여부 결정 필요 |

---

## 5. 위험 9개 + 대응 SPEC 매핑

| 위험 | 위험도 | 대응할 SPEC (신규/보강) |
|---|---|---|
| 4-1 콘텐츠 마이그레이션 (가장 큰 함정) | Critical | SPEC-CMS-MIG-001 (신규): 비즈패스파인더 데이터 양/구조 사전조사 → 매핑표 → 리허설 2회 → cutover |
| 4-2 에디터 호환성 | Major | SPEC-CMS-EDITOR-001: 기존 콘텐츠 분석·정리(sanitize 룰)·이미지 절대경로 일괄치환 |
| 4-3 권한 체계 재설계 | Major | SPEC-CMS-AUTH-001 (회원·권한 재작성): SSO + 4단계 RBAC + 부서 + 휴면/탈퇴 |
| 4-4 통합 검색 인덱싱 | Major | SPEC-CMS-SEARCH-001: 검색엔진 도입·도메인 사전 보강·인덱싱 정책 |
| 4-5 시각화 대시보드 후행 | Critical | 별도 critical path: 디자인 시안 + 시각화 라이브러리 결정을 분석 단계 끝에 확정 |
| 4-6 알림톡 카카오 검수 | Major | SPEC-CMS-NOTI-001: 카카오 비즈채널 사전확인·템플릿 N개 사전 검수 일정 |
| 4-7 검색·추천 데이터 표준 충돌 | Major | SPEC-CMS-GOV-001: 정책/업종/지역 코드 표준화 매핑표 |
| 4-8 첨부파일 보안 | Minor | SPEC-CMS-FILE-001: 매직넘버·다운로드 권한·한글파일명 RFC 5987 |
| 4-9 통합 홈페이지 이관 | Critical | SPEC-CMS-MIG-001 통합: SubURL 매핑·도메인 변경·쿠키 정책 |

---

## 6. 신규 SPEC 트리 제안

```
SPEC-CMS-101 [umbrella v2]            ← 1차 출시 전체 범위 (RFP COR/SFR/INR/SER 매핑)
├─ 인증·SSO·권한 (CORE)
│   ├─ SPEC-CMS-AUTH-001 (SSO + 4단계 RBAC + 부서 + IP 화이트리스트)
│   └─ SPEC-CMS-AUTH-002 (권한 변경 이력 + 비인가 차단 — SFR-014)
├─ 콘텐츠
│   ├─ SPEC-CMS-CONTENT-001 (다중 게시판 + 워크플로 + 댓글)
│   ├─ SPEC-CMS-CONTENT-002 (공지·자료실·설문)
│   ├─ SPEC-CMS-CONTENT-003 (안전경영 가이드라인 — SFR-006)
│   ├─ SPEC-CMS-CONTENT-004 (정책사업 — SFR-007)
│   ├─ SPEC-CMS-CONTENT-005 (사고사례 — SFR-004)
│   ├─ SPEC-CMS-CONTENT-006 (통계·지표·경영지표)
│   └─ SPEC-CMS-CONTENT-007 (알림톡/메일 템플릿 — SFR-008)
├─ 운영·관리
│   ├─ SPEC-CMS-ADMIN-001 (메뉴·사이트·템플릿)
│   ├─ SPEC-CMS-ADMIN-002 (조직·부서·사용자 관리)
│   ├─ SPEC-CMS-ADMIN-003 (배치 모니터링 + 시스템 로그 SFR-015)
│   └─ SPEC-CMS-ADMIN-004 (KPI 대시보드 SFR-013)
├─ 횡단 (Cross-cutting)
│   ├─ SPEC-CMS-SEARCH-001 (통합 검색 + 자동완성 — SFR-009)
│   ├─ SPEC-CMS-VIZ-001    (시각화 대시보드 — SFR-009)
│   ├─ SPEC-CMS-NOTI-001   (알림톡/메일 발송 엔진 — SFR-008)
│   ├─ SPEC-CMS-FILE-001   (첨부 보안 + 통합파일서버 — SER-006)
│   ├─ SPEC-CMS-GOV-001    (데이터 거버넌스 콘솔 — SFR-011)
│   ├─ SPEC-CMS-AUDIT-001  (감사로그 + 접속로그 + 다운로드이력 — SFR-015)
│   ├─ SPEC-CMS-A11Y-001   (KWCAG 2.2 + SEO + 반응형)
│   └─ SPEC-CMS-MIG-001    (콘텐츠 마이그레이션 + 통합 홈페이지 이관 — SFR-010)
└─ AI/ML 트랙 (별도 130일 일정)
    ├─ SPEC-CMS-AI-001 (Milvus + 임베딩 파이프라인)
    ├─ SPEC-CMS-AI-002 (정책 매칭 알고리즘 — SFR-007 연계)
    ├─ SPEC-CMS-AI-003 (RAG 질의응답)
    └─ SPEC-CMS-AI-004 (알고리즘 품질 모니터링 — SFR-012)
```

CMS 트랙 약 18개 SPEC + AI 트랙 4개 SPEC = 22개 SPEC 후보

180일/270~300인일 일정에 맞추려면 P0만 1차 출시(약 12개 SPEC), P1은 2차 운영 단계로 분리 권장.

---

## 7. RFP 원문 수령 후 결정해야 할 항목

| ID | 항목 | 확인 위치 |
|---|---|---|
| D-1 | RFP 원문 PDF/한글 파일 → `.moai/refs/RFP/` 저장 위치 | 도착 즉시 |
| D-2 | eGovFrame 정확 버전 (3.10 / 4.x) | RFP 본문 또는 별첨 |
| D-3 | 발주기관 + 비즈패스파인더 운영기관명 | RFP 표지 |
| D-4 | 기존 데이터 양(게시글, 첨부, 회원) | 별첨 또는 사전질의 |
| D-5 | 카카오 비즈채널/발신번호 보유 여부 | 발주처 확인 |
| D-6 | 디자인 시안 일정 (시각화 critical path) | RFP 일정표 |
| D-7 | 검색엔진 운영비 부담 주체 | RFP 사업비 |
| D-8 | 워크플로 도입 여부 (분석 단계 결정) | 분석 단계 |

---

## 8. 권장 다음 단계

1. RFP 원문 점검 → `.moai/refs/RFP/` 폴더 내용 확인
2. RFP 원문 핵심 발췌(SFR/COR/INR/SER 조항 정리) → `.moai/refs/rfp-summary.md`
3. `/moai project` 재실행: tech.md, structure.md를 RFP 기반으로 재작성
4. SPEC-CMS-101 umbrella v2 신규 작성 + 22개 SPEC 트리 + RFP 조항 1:1 매핑
5. P0 SPEC 12개 우선 작성 (CMS 트랙) + AI 트랙 4개 별도 진행
6. 마이그레이션 SPEC을 분석 단계 critical path에 배치

---

## 9. 변경 이력

| 버전 | 일자 | 변경 내용 | 작성 |
|---|---|---|---|
| v0.1 | 2026-04-29 | 초안 (RFP 검토 문서 기반 1차 갭 분석) | MoAI orchestrator |

(RFP 원문 검토 후 v0.2 보강 예정)
