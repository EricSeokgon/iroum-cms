# SPEC-CMS-006 Research Notes

본 research 문서는 SPEC-CMS-006 (안전경영 가이드라인 + 사고사례 매칭) 1차 설계의 8개 핵심 결정 포인트에 대한 비교·분석·권장안을 기록한다. 모든 결정은 v0.1 범위에서의 선택이며, v0.2+ 또는 옵션 SPEC (SPEC-CMS-AI-001)에서 재평가될 수 있다.

---

## 1. 외부 사고 데이터 수집 — OpenAPI vs 크롤링 vs CSV 수동 다운로드

### 옵션 비교

| 옵션 | 장점 | 단점 |
|---|---|---|
| 안전보건공단 OpenAPI (REST/JSON) | 표준 인증·갱신·페이지네이션, ToS 명확, 자동화 용이 | 인증키 발급 절차 (사용자 결정 사항), 일부 데이터셋 미제공 가능 |
| 웹 크롤링 (BeautifulSoup/Jsoup) | 게시판형 데이터 수집 가능 | ToS 위반 위험, 사이트 변경에 취약, robots.txt 준수 필요 |
| CSV/Excel 수동 다운로드 (월 1회) | 공식 공개 데이터셋, 안정적 | 자동화 어려움, 갱신 지연 |

### 권장안 (1차)

- **OpenAPI 우선** (안전보건공단 산업재해 통계 API, 고용노동부 통계 API)
- **사고백서 CSV 보조** (월 1회 관리자 수동 업로드 + 정제 파이프라인 재사용)
- 크롤링은 1차 범위에서 제외 (법적·안정성 위험)

### 후속 조치

- KOSHA OpenAPI 인증키 발급 주체 (이로움/발주기관 중 누가) 사용자 확정 필요
- API rate limit (분/일별 호출 제한) 사전 검증
- 환경변수 관리: `KOSHA_API_KEY`, `MOEL_API_KEY` (하드코딩 금지)

---

## 2. 매칭 알고리즘 — 키워드 가중치 vs 벡터 임베딩 vs 하이브리드

### 옵션 비교

| 옵션 | 정확도 (예상) | 인프라 부담 | 설명 가능성 (XAI) | 1차 적합성 |
|---|---|---|---|---|
| 키워드 가중치 (TF-IDF 변형) | 중 (Top-1 60~75%) | 낮음 (PostgreSQL only) | 높음 (어떤 키워드 기여) | **적합** |
| 벡터 임베딩 (sentence-transformers + Milvus/pgvector) | 고 (Top-1 80~90% 추정) | 높음 (Milvus 인프라 또는 pgvector + 임베딩 모델) | 중 (코사인 유사도 점수만) | 부적합 (인프라 frozen) |
| 하이브리드 (키워드 1차 + 임베딩 재정렬) | 고 | 중~고 | 중~고 | v0.2+ |

### 권장안 (1차)

- **키워드 가중치** (industry 0.4 + process 0.3 + hazard 0.2 + equipment 0.1) 채택
- 동의어 사전으로 1차 정확도 보강 (mecab-ko 없이 PostgreSQL `pg_trgm` + 명시적 synonym)
- 벡터 임베딩은 SPEC-CMS-AI-001 (옵션 트랙)으로 분리 — Milvus 또는 pgvector 도입 결정 시점에 활성화

### 가중치 근거

- INDUSTRY 0.4: 업종이 다르면 사고 양상이 매우 달라짐 (가장 강한 신호)
- PROCESS 0.3: 동일 업종 내에서도 공정에 따라 사고 유형 다변화
- HAZARD 0.2: 위험요소는 업종·공정에서 일부 도출되므로 보조 신호
- EQUIPMENT 0.1: 가장 약한 신호 (장비는 다양하고 상호 교체 가능)

가중치는 `.moai/config/sections/safety.yaml`에 외부화하여 운영 중 튜닝 가능 (단, 합 = 1.0 검증).

---

## 3. 한국어 형태소 분석 — mecab-ko vs okt vs PostgreSQL pg_trgm

### 옵션 비교

| 옵션 | 정확도 | Java 호환 | 의존성 |
|---|---|---|---|
| mecab-ko + mecab-ko-dic | 높음 (산업 표준) | JNI 필요 (komoran/seunjeon 가능) | C 라이브러리 + 사전 |
| Open Korean Text (OKT) | 중상 | Java native (Scala) | jvm only |
| Komoran | 중상 | Java native | jvm only |
| PostgreSQL pg_trgm + 명시 synonym 사전 | 중 | DB 기능 | 없음 |

### 권장안 (1차)

- **PostgreSQL `pg_trgm` + 명시적 synonym 사전** 채택 (1차)
- 운영 중 매칭 누락 분석 결과 기반 v0.2+에서 OKT 또는 Komoran 도입 검토
- mecab-ko는 운영 부담 (JNI, 사전 갱신) 대비 1차 효익 부족

### 구현 메모

- `safety_keyword_synonym` 테이블에 동의어 명시
- 매칭 시 synonym → keyword 정규화 후 가중치 계산
- 신조어·신산업 키워드는 관리자 수동 추가 (REQ-SAFETY-001-D-5)

---

## 4. 가이드라인 PDF 변환 — wkhtmltopdf vs Puppeteer vs OpenHTMLtoPDF

### 옵션 비교

| 옵션 | Java native | KWCAG 호환 | 한글 폰트 임베드 | 의존성 |
|---|---|---|---|---|
| wkhtmltopdf | 외부 프로세스 | 중 (CSS 일부 한계) | 가능 | 외부 binary |
| Puppeteer (headless Chrome) | Node.js | 높음 | 가능 | Node.js + Chrome |
| OpenHTMLtoPDF | **Java native** | 높음 (PDF/UA 지원) | 우수 (Noto Sans KR) | 없음 (Maven) |
| iText 7 | Java native | 높음 | 가능 | AGPL/상용 라이선스 |

### 권장안 (1차)

- **OpenHTMLtoPDF 우선** (Java native, Spring Boot 친화, KWCAG 호환 우수, 라이선스 무료)
- 폴백: wkhtmltopdf 외부 프로세스 (특수 CSS 필요한 경우)
- iText는 라이선스 이슈 회피

### 비동기 처리

- Spring `@Async` + bounded executor 풀 (4~8 workers)
- 동시 변환 ≤ 5건 (RISK-S6 OOM 방지)
- 완료 시 알림 (이메일/카카오)

---

## 5. 매칭 결과 캐시 — Caffeine vs Redis vs DB row

### 옵션 비교

| 옵션 | 응답 시간 | 분산 환경 | 운영 부담 |
|---|---|---|---|
| Caffeine (인메모리) | 매우 빠름 (μs) | 단일 인스턴스 한정 | 낮음 (Spring Boot 자동 구성) |
| Redis (외부 캐시) | 빠름 (ms) | 분산 가능 | 중 (Redis 인프라 필요) |
| DB row (safety_match_result, expires_at) | 느림 (ms~십수ms) | 분산 가능 | 낮음 (이미 PostgreSQL 사용) |

### 권장안 (1차)

- **Caffeine + DB row 병행** (TTL 1시간)
- Caffeine: 단일 인스턴스 fastpath (응답 < 500ms 보장)
- DB row (safety_match_result): cross-instance fallback + 감사 로그
- 인스턴스 수가 1대로 운영되는 한 Caffeine만으로 충분
- 다중 인스턴스로 확장 시 v0.2+에서 Redis 도입 (이때 Caffeine은 L1, Redis는 L2)

### 무효화 전략

- 프로필 변경 시 해당 profileId 캐시 키 evict
- 외부 데이터 동기화 완료 시 전체 evict (`clearAll`)
- TTL 만료 시 자연 evict

---

## 6. 키워드 사전 관리 — 전문가 수동 vs 자동 학습 vs 외부 사전

### 옵션 비교

| 옵션 | 정확도 | 운영 부담 | 신뢰성 |
|---|---|---|---|
| 전문가 수동 등록 + 관리자 화면 | 높음 | 중 (분기 검토) | 높음 |
| 자동 학습 (TF-IDF 추출 후 검수) | 중 | 낮음 | 중 (검수 미흡 시 위험) |
| 외부 사전 (KOSHA 표준어 사전) | 높음 (도입 시) | 낮음 | 높음 |

### 권장안 (1차)

- **전문가 수동 등록 + 관리자 화면** (REQ-SAFETY-005-D-1, REQ-SAFETY-001-D-5)
- 초기 키워드 사전: 안전보건공단 표준 분류체계 + 산업재해 통계 카테고리 활용
- 외부 사전 직접 import는 ToS 확인 후 v0.2+ 검토
- 자동 학습은 매칭 누락 로그 분석 도구로 보조 (관리자 추천 기능, v0.3+)

### 초기 사전 구축 범위

- INDUSTRY: KSIC 9차 5자리 코드 + 한글명 (~1,200건)
- PROCESS: 주요 30~50개 공정 유형 (조립/용접/운반/굴착/도장 등)
- HAZARD: 13대 사망사고 위험요소 + 확장 (추락/끼임/충돌/감전/화재/폭발/유해물질/...)
- EQUIPMENT: 50~100개 주요 장비/설비

---

## 7. 가이드라인 신뢰성 — 법무·산업안전 검토 메타데이터

### 문제

자동 생성된 가이드라인을 법적 책임 자료로 활용 시 잘못된 정보로 인한 기업 손해 가능. 신뢰성 표시 메커니즘 필요 (RISK-S4).

### 권장안

`safety_guideline_template`에 검토 메타데이터 컬럼 추가:

- `review_status` VARCHAR(20): `LEGAL_REVIEWED` / `SAFETY_REVIEWED` / `NONE`
- `reviewed_by` BIGINT: 검토자 user id
- `reviewed_at` TIMESTAMPTZ: 검토 일시

화면 표시 정책:

- `LEGAL_REVIEWED`: 녹색 배지 "법무 검토 완료"
- `SAFETY_REVIEWED`: 청색 배지 "산업안전 검토 완료"
- `NONE`: 회색 배지 + **"참고용" 워터마크 강제** (PDF/HTML 모두) + 본문 상단 면책 문구

운영 정책 (사용자 확정 필요):

- 검토 주체 (내부 안전팀 / 외부 법무법인) 결정
- 검토 주기 (분기/반기) 및 갱신 트리거 (법령 변경 시)
- 검토 미완료 템플릿 PUBLISHED 허용 여부 (1차 권장: 허용 + 워터마크)

---

## 8. 사고사례 익명화 — 정규식 + 화이트리스트 vs LLM 기반 PII 마스킹

### 옵션 비교

| 옵션 | 정확도 | 운영 부담 | 결정성 |
|---|---|---|---|
| 정규식 + 화이트리스트 (한국 이름 패턴, 회사명 키워드) | 중상 | 낮음 | 높음 (재현 가능) |
| LLM 기반 (NER 모델) | 높음 | 높음 (모델 운영) | 낮음 (비결정적) |
| 하이브리드 (정규식 1차 + LLM 검수) | 매우 높음 | 매우 높음 | 중 |

### 권장안 (1차)

- **정규식 + 화이트리스트** (1차 채택, 결정적·감사 가능)
- 정규식 패턴:
  - 한국 이름: `[가-힣]{2,4}(?=\\s*(씨|군|양|작업자|근로자|피해자|책임자))` 매칭 시 `[익명]` 치환
  - 회사명: 화이트리스트 (예: 안전보건공단·고용노동부는 보존, 사기업은 `[기업A]/[기업B]/...` 순서대로 치환)
- 관리자 검수 단계 의무화 (REQ-SAFETY-001-D-5의 익명화 보강)
- LLM 기반은 v0.4+ 검토 (운영 부담 vs 효익 평가 후)

### 회귀 테스트

- 알려진 사고사례 100건 샘플 셋 + 익명화 후 검출 0건 회귀 테스트 (QG-SAFETY-1)
- 신규 패턴 발견 시 화이트리스트·정규식 추가 + 회귀 테스트 누적

---

## 부록 A. 관련 SPEC 의존성

- SPEC-CMS-002 (회원·권한): `member`, `users` 테이블, RBAC, AES-256-GCM 식별번호 암호화
- SPEC-CMS-003 (게시판): SQL Injection / XSS / 파일 보안 정책 재사용
- SPEC-CMS-004 (콘텐츠/배너): Handlebars 변수 치환 엔진 재사용 (v0.2)
- SPEC-CMS-MEDIA-001: 체크리스트 증빙 첨부 (`evidence_attachment_uuid` 참조)
- SPEC-CMS-AI-001 (옵션, 별도 SPEC): 벡터 임베딩 매칭 v0.2+ 활성화

## 부록 B. 후속 결정 사항 (사용자 확정 필요)

1. **KOSHA OpenAPI 인증키 발급 주체**: 이로움 자체 발급 vs 발주기관 위임
2. **가이드라인 법무·산업안전 검토 정책**: 검토 주체 (내부 안전팀 / 외부 법무법인), 주기, 갱신 트리거
3. (보조) 가이드라인 PDF 한글 폰트 라이선스: Noto Sans KR (OFL) 채택 권장 — 무료·웹폰트 가능
