# SPEC-CMS-010 Acceptance Criteria

> 본 문서는 spec.md의 모든 sub-REQ에 대응하는 Given/When/Then 인수 조건을 정의한다. 자동화 검증은 JUnit 5 + Testcontainers(PostgreSQL 16, `pg_trgm` 확장 필수) + Spring Boot Test + Spring MockMvc, 수동 검증은 운영자 검수 표시. 본 SPEC은 SPEC-CMS-003/004/006/MEDIA-001 마이그레이션이 이미 구축한 `search_vector`/`tsv_ko`/`tsv_en`/`pg_trgm` GIN 인프라를 입력으로 사용하므로, 해당 인덱스 자체의 재검증은 원천 SPEC acceptance.md를 따른다. SPEC-CMS-009 `batch_execution_log`/`retention_policy`는 본 SPEC 인기 검색어 배치·로그 보존의 인프라로 재사용된다.

---

## A. 통합 검색 (REQ-SEARCH-001 ~ 004)

### REQ-SEARCH-001 — 통합 검색 API 기본 동작

**Given** 게시판(`bbs_post`)에 `title='서울시 청년 정책 안내'` 게시글 1건, 콘텐츠 페이지(`page`)에 `title='서울 청년 지원 사업'`(visibility=PUBLIC) 1건, 정책(`policies`)에 `title='청년 월세 지원'` 1건이 존재하고, locale=ko로 모두 인덱싱되어 있고
**When** 비로그인 사용자가 `GET /api/v1/search?q=청년&domain=ALL&page=1&size=20&locale=ko` 를 호출하면
**Then** 200 OK + `totalElements >= 3`이 반환되고, 응답의 `content[]`에는 3건이 모두 포함되며, 각 결과의 `docType`이 (`board`, `content`, `policy`) 중 하나로 분류된다.

**And** `facets.byDomain`에 `{ board:1, content:1, policy:1, safety:0, media:0, publication:0 }` 카운트가 정확히 반환된다.

**And** 각 결과에 `(docType, docId, title, snippet, highlight, rank, domain, url, createdAt)` 9개 필드가 모두 존재한다.

**And** 응답의 `responseMs`가 0보다 크고 500ms 미만이다 (PER-003).

**And When** 동일 요청을 100회 반복했을 때
**Then** p95 응답 시간이 500ms 미만이다 (REQ-SEARCH-010 성능 검증).

---

### REQ-SEARCH-001 — 도메인 가중치 정렬 검증

**Given** 동일한 단어 "청년"이 `bbs_post.title`(가중치 1.0), `page.title`(0.9), `media_asset.metadata`(0.5)에 동일 빈도로 등장하고, 각 도메인의 `ts_rank_cd` 원시 점수가 동일하게 0.5라고 가정하면
**When** `GET /api/v1/search?q=청년&domain=ALL` 을 호출하면
**Then** 결과 순서는 (1) board (0.5×1.0=0.5), (2) content (0.5×0.9=0.45), (3) media (0.5×0.5=0.25) 순으로 정렬되어 반환된다.

**And When** size=50을 초과한 size=100을 전달하면
**Then** 400 Bad Request + 에러 코드 `SEARCH_QUERY_TOO_LONG` 또는 `SEARCH_INVALID_SIZE` 가 반환된다.

---

### REQ-SEARCH-002 — 검색 결과 하이라이트 + XSS sanitize

**Given** `bbs_post.content='서울시 청년 정책 안내문입니다'` 게시글이 인덱싱되어 있고
**When** `GET /api/v1/search?q=청년&locale=ko` 를 호출하면
**Then** 응답의 해당 결과 `highlight` 필드에 `'서울시 <mark>청년</mark> 정책 안내문입니다'` 형태로 `<mark>` 태그가 정확히 적용된다.

**And** `MaxWords=30, MinWords=5, MaxFragments=2` 옵션이 적용되어 본문 길이가 제한된다.

**And** `<mark>`의 속성(class, style, onclick 등)은 모두 제거되어 있다 (sanitize).

**Given** 악의적 입력으로 `bbs_post.content='<script>alert(1)</script> 청년 <img src=x onerror=alert(2)>'` 가 인덱싱되어 있고
**When** `GET /api/v1/search?q=청년` 을 호출하면
**Then** 응답 `highlight` 에서 `<script>`, `<img>`, `onerror` 등 `<mark>` 외 모든 태그·속성이 제거된 안전한 문자열만 반환된다.

---

### REQ-SEARCH-003 — 비공개 콘텐츠 권한 가드 (silent 제외)

**Given** QnA 비공개 게시글 (`is_secret=true`, author_id=10) 1건과 공개 게시글 1건이 모두 키워드 "민원"과 매칭되고
**When** 비로그인 사용자가 `GET /api/v1/search?q=민원&domain=ALL` 를 호출하면
**Then** 비공개 1건은 `content[]`에서 제외되고, `facets.byDomain.board`도 비공개 제외 후 카운트로 반환된다 (silent 제외, 403 아님).

**And When** 동일 요청을 본인(`user_id=10`)으로 인증하여 호출하면
**Then** 비공개 게시글이 결과에 포함되어 반환된다.

**And When** ROLE=ADMIN 사용자로 호출하면
**Then** 비공개 게시글이 결과에 포함된다.

**And When** 다른 사용자(`user_id=11`)로 호출하면
**Then** 비공개 게시글은 silent 제외된다 (정보 누출 방지).

---

### REQ-SEARCH-004 — 도메인별 검색 단일 도메인 제한

**Given** 모든 6개 도메인에 키워드 "정책"이 매칭되는 데이터가 존재하고
**When** `GET /api/v1/search?q=정책&domain=board` 를 호출하면
**Then** 응답 `content[]`의 모든 결과가 `docType='board'` 이며, `facets.byDomain` 에는 `board` 키만 존재한다.

**And When** `domain=publication` 를 호출하면
**Then** `bbs_post WHERE board_type='PUBLICATION'` 만 매칭된 결과가 반환된다.

**And When** `domain=invalid_value` 를 호출하면
**Then** 400 Bad Request + 에러 코드 `SEARCH_DOMAIN_INVALID` 가 반환된다.

**And When** `domain` 파라미터를 생략하면
**Then** `domain=ALL` 로 동작한다 (default).

---

## B. 자동완성 (REQ-SEARCH-005)

### REQ-SEARCH-005 — 자동완성 API 통합 응답

**Given** `search_popular_cache(period_type='DAILY')` 에 `query='서울시 청년 정책', search_count=120, locale='ko'` 행이 존재하고, `bbs_post.title='서울시 청년 지원금 안내'` 행이 존재하고
**When** `GET /api/v1/search/autocomplete?prefix=서울시%20청&limit=10&locale=ko` 를 호출하면
**Then** 200 OK + `items[]` 에 `(text='서울시 청년 정책', source='popular', score≥0.3)` 와 `(text='서울시 청년 지원금 안내', source='title', score≥0.3)` 가 모두 포함된다.

**And** `items[]` 는 score 내림차순으로 정렬되어 있다.

**And** `items.length <= 10` 이다.

**And** 응답 시간이 100ms 미만이다 (REQ-SEARCH-010).

**And When** `prefix='서'` (1자) 로 호출하면
**Then** 200 OK + `items: []` (빈 배열) 이 반환된다 (1자 prefix 처리, RISK-S-09).

**And When** `prefix='가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차'` (51자) 로 호출하면
**Then** 400 Bad Request + 에러 코드 `SEARCH_QUERY_TOO_LONG` 가 반환된다.

---

### REQ-SEARCH-005 — 자동완성 폴백 동작

**Given** `search_popular_cache` 가 비어 있고 (배치 미실행 상태) `bbs_post.title` 만 존재하고
**When** `GET /api/v1/search/autocomplete?prefix=청년&locale=ko` 를 호출하면
**Then** 200 OK + 콘텐츠 제목 prefix 매칭 결과만 단독으로 반환된다 (인기 검색어 cache 미존재 시 graceful degradation).

**And When** `pg_trgm` similarity 0.3 미만으로 매칭되는 행만 존재하면
**Then** 해당 행은 응답에서 제외되어 `items: []` 또는 부분 결과가 반환된다.

---

## C. 인기 검색어 (REQ-SEARCH-006 ~ 007)

### REQ-SEARCH-006 — 인기 검색어 조회

**Given** `search_popular_cache` 에 `period_type='DAILY', period_date='2026-05-06', locale='ko'` 로 (`rank=1, query='서울시 청년', search_count=1542`), (`rank=2, query='교통 안전', search_count=987`) 가 적재되어 있고
**When** `GET /api/v1/search/popular?period=DAILY&locale=ko&limit=10` 를 호출하면
**Then** 200 OK + 응답에 `period='DAILY', periodDate='2026-05-06'` 와 `items[]` (rank 오름차순) 가 반환된다.

**And** `items[0]` = `{ rank:1, query:'서울시 청년', searchCount:1542 }`, `items[1]` = `{ rank:2, query:'교통 안전', searchCount:987 }`.

**And** 응답 시간이 50ms 미만이다 (REQ-SEARCH-010).

---

### REQ-SEARCH-006 — 캐시 미스 폴백

**Given** `period_type='DAILY', period_date='2026-05-07'` 데이터는 없고, `period_date='2026-05-06'` 데이터만 존재하고
**When** `GET /api/v1/search/popular?period=DAILY&locale=ko` 를 호출하면
**Then** 시스템은 가장 최근 사용 가능한 `period_date='2026-05-06'` 으로 폴백하여 결과를 반환한다.

**And When** 모든 period_date 에 데이터가 없으면
**Then** 200 OK + `items: []` 가 반환된다 (404 아님).

**And When** `locale='zh'` (지원하지 않는 locale) 로 호출하면
**Then** 400 Bad Request + 에러 코드 `SEARCH_LOCALE_UNSUPPORTED` 가 반환된다.

---

### REQ-SEARCH-007 — 일별 인기 검색어 집계 배치

**Given** `search_log` 에 어제 일자(2026-05-06) 동일 `normalized_query='서울시 청년'` 행 50건, `normalized_query='교통 안전'` 행 30건, `normalized_query='기타'` 행 2건(< 3 임계 미달)이 적재되어 있고, 모든 행 `result_count > 0, locale='ko'` 이고
**When** 2026-05-07 04:30 에 `PopularQueryAggregateDailyJob` 이 실행되면
**Then** `search_popular_cache` 에 `(period_type='DAILY', period_date='2026-05-06', locale='ko', query='서울시 청년', search_count=50, rank=1)` 와 `('교통 안전', 30, rank=2)` 행이 UPSERT 된다.

**And** `'기타'` (3건 미만) 행은 cache 에 적재되지 않는다 (잡음 제거, §7.3 HAVING 절).

**And** `search_log.result_count = 0` 행은 집계에서 제외된다.

**And** SPEC-CMS-009 `batch_execution_log` 에 (`job_name='PopularQueryAggregateDailyJob', job_group='SEARCH', status='SUCCESS', records_processed=2`) 가 적재된다.

---

### REQ-SEARCH-007 — 주별/월별 집계 + 재시도

**Given** `search_log` 에 직전 주(2026-04-27 ~ 2026-05-03) 데이터가 적재되어 있고
**When** 2026-05-04 (월) 05:00 에 `PopularQueryAggregateWeeklyJob` 이 실행되면
**Then** `search_popular_cache(period_type='WEEKLY', period_date='2026-04-27')` 에 직전 주 TOP 100 이 UPSERT 된다.

**And When** 2026-05-01 05:30 에 `PopularQueryAggregateMonthlyJob` 이 실행되면
**Then** `search_popular_cache(period_type='MONTHLY', period_date='2026-04-01')` 에 전월 4월 TOP 100 이 UPSERT 된다.

**And When** 배치가 DB 일시 장애로 1차 실패한 후 1시간 후 재시도가 성공하면
**Then** `batch_execution_log.retry_count=1, status='SUCCESS'` 행이 적재된다.

**And When** 3회 재시도 모두 실패하면
**Then** `batch_execution_log.status='FAILURE'` + SPEC-CMS-005 운영자 알림 큐에 push 된다 (SPEC-CMS-009 REQ-GOV-010 정책 일치).

---

## D. 검색 로그 (REQ-SEARCH-008)

### REQ-SEARCH-008 — 검색 로그 비동기 적재

**Given** 인증된 사용자 `user_id=10, session_id='sess-xyz'` 가
**When** `GET /api/v1/search?q=서울%20청년&domain=ALL&locale=ko` 를 호출하면
**Then** 검색 응답이 즉시 반환되고, 비동기로 `search_log` 에 (`user_id=10, session_id='sess-xyz', query='서울 청년', normalized_query='서울 청년', result_count, response_ms, locale='ko', domain_filter='ALL', ip_hash`) 행이 INSERT 된다.

**And** `created_at` 이 정확히 기록된다.

**And** 비로그인 사용자 호출 시 `user_id=NULL, session_id=쿠키 기반 세션` 으로 적재된다.

**And** 검색 응답 시간 측정값과 `search_log.response_ms` 의 차이가 ±50ms 이내이다 (비동기 적재 영향 < 10ms 입증, JMeter wallclock 기준).

**And** 자동완성 API 호출(`prefix` 길이 ≥ 2) 시에도 동일 로그가 적재된다.

**And** 자동완성 `prefix` 길이 < 2 인 경우 로그 적재가 스킵된다 (잡음 제거).

---

### REQ-SEARCH-008 — 클릭 추적

**Given** `search_log` 에 `id=100, session_id='sess-xyz', created_at=NOW() - INTERVAL '5 minutes'` 행이 존재하고
**When** 동일 session_id 사용자가 `POST /api/v1/search/click` body=`{searchLogId:100, docType:'board', docId:12345, rank:3}` 를 호출하면
**Then** 204 No Content + `search_log.id=100` 의 (`clicked_doc_type='board', clicked_doc_id=12345, clicked_rank=3, clicked_at=NOW()`) 로 UPDATE 된다.

**And When** `searchLogId=100` 의 `created_at` 이 30분 이전(`NOW() - INTERVAL '31 minutes'`) 이면
**Then** 410 Gone + 에러 코드 `SEARCH_CLICK_WINDOW_EXPIRED` 가 반환된다.

**And When** 다른 session_id 사용자가 `searchLogId=100` 으로 클릭 추적을 시도하면
**Then** 403 Forbidden 이 반환된다 (RISK-S-07 방어).

**And When** 존재하지 않는 `searchLogId=99999` 이면
**Then** 404 Not Found 가 반환된다.

---

### REQ-SEARCH-008 — 보존 정책 자동화

**Given** `search_log` 에 7개월 전 행 1000건 + 5개월 전 행 500건이 존재하고, SPEC-CMS-009 `retention_policy(target_table='search_log', policy_type='DELETE', retention_months=6, schedule_cron='0 30 5 1 * *')` 가 ACTIVE 상태이고
**When** 매월 1일 05:30(또는 §7.1 분산 시각 05:35)에 SPEC-CMS-009 retention 배치가 실행되면
**Then** 7개월 경과 1000건이 DELETE 되고, 5개월 행 500건은 그대로 유지된다.

**And** SPEC-CMS-009 `batch_execution_log(job_group='RETENTION', records_processed=1000)` 가 적재된다.

---

## E. 동의어 (REQ-SEARCH-009)

### REQ-SEARCH-009 — 동의어 확장

**Given** ADMIN 운영자가 `POST /api/v1/search/synonyms` body=`{term:'수도', synonym:'서울', locale:'ko'}` 로 동의어를 등록하여 `search_synonym` 에 ACTIVE 상태로 적재되어 있고, `bbs_post.title='서울시 정책'` 게시글이 인덱싱되어 있고
**When** 사용자가 `GET /api/v1/search?q=수도&locale=ko` 를 호출하면
**Then** 응답 `expandedQuery` 가 `'수도 | 서울'` 형태로 포함되고, `bbs_post.title='서울시 정책'` 결과가 매칭되어 반환된다.

**And** SPEC-CMS-005 `audit_log` 에 (`action=CREATE, entity_type=search_synonym, entity_id`) 가 자동 적재된다 (AOP).

---

### REQ-SEARCH-009 — 동의어 CRUD 권한 + 제약

**Given** USER 권한 토큰으로
**When** `POST /api/v1/search/synonyms` 를 시도하면
**Then** 403 Forbidden 이 반환된다 (ADMIN 한정).

**And When** ADMIN 으로 `{term:'수도', synonym:'수도', locale:'ko'}` 등록을 시도하면 (term=synonym 자기참조)
**Then** 400 Bad Request + 에러 코드 `SEARCH_SYNONYM_SELF` 가 반환된다 (chk_ss_self CHECK 제약).

**And When** ADMIN 으로 동일 (`term='수도', synonym='서울', locale='ko'`) 를 재등록하면
**Then** 409 Conflict + 에러 코드 `SEARCH_SYNONYM_DUPLICATE` 가 반환된다 (uk_ss_term_synonym_locale UNIQUE).

**And When** ADMIN 이 `DELETE /api/v1/search/synonyms/{id}` 를 호출하면
**Then** 200 OK + `search_synonym.status='PAUSED'` 로 soft delete 되고 (hard DELETE 아님), 검색 시 해당 동의어는 더 이상 확장되지 않는다.

---

### REQ-SEARCH-009 — 토큰 폭발 방지

**Given** `search_synonym` 에 동일 `term='A'` 로 25개 synonym 이 ACTIVE 상태로 등록되어 있고
**When** 사용자가 `q=A` 를 검색하면
**Then** 시스템은 ts_query 토큰 수를 상위 빈도 20개로 절단하여 OR 확장한다 (RISK-S-05 방어).

**And** 응답 `expandedQuery` 에는 절단된 20개만 포함된다.

**And When** ADMIN 이 `term='수도'` 에 대해 `synonym='서울'`(locale=ko) 등록 후, transitive 확장 시도(`synonym='Seoul'` 을 'ko' 로 등록)는 허용되나, 검색 시 'ko' synonym 만 확장되고 cross-locale 확장은 발생하지 않는다 (locale 분리).

---

## F. 비기능 요구사항 (REQ-SEARCH-010)

### F-1 성능

**Given** Testcontainers PostgreSQL 16 환경에 `bbs_post` 5만 행, `page` 3만 행, `policies` 2만 행 (총 10만 문서) 이 인덱싱되어 있고, JMeter 100 thread × 10 iter (총 1000 요청) 부하 환경에서
**When** `GET /api/v1/search?q=청년&domain=ALL&locale=ko` 를 호출하면
**Then** p95 응답 시간이 500ms 미만이다.

**And When** `GET /api/v1/search/autocomplete?prefix=청년&locale=ko` 를 100 thread × 10 iter 부하로 호출하면
**Then** p95 응답 시간이 100ms 미만이다.

**And When** `GET /api/v1/search/popular?period=DAILY&locale=ko&limit=10` 을 100 thread × 10 iter 부하로 호출하면
**Then** p95 응답 시간이 50ms 미만이다 (cache 히트).

**And When** `PopularQueryAggregateDailyJob` 이 search_log 100만 행 환경에서 실행되면
**Then** finished_at - started_at < 10분 (600,000ms) 를 충족한다 (PER-003).

---

### F-2 보안

**Given** USER 권한 토큰으로
**When** `POST /api/v1/search/synonyms`, `PUT /api/v1/search/synonyms/{id}`, `DELETE /api/v1/search/synonyms/{id}`, `GET /api/v1/search/stats/queries` 4개 엔드포인트를 호출하면
**Then** 모두 403 Forbidden 이 반환된다.

**And When** ADMIN 권한으로 동일 요청을 보내면
**Then** 정상 응답된다.

**And When** 검색 query 에 `'; DROP TABLE bbs_post; --` 같은 SQL injection 시도가 포함되면
**Then** prepared statement 바인딩으로 무력화되고 200 OK + 0건 결과가 반환된다 (테이블 손상 없음).

**And When** `bbs_post.content` 에 악의적 XSS 페이로드가 적재되어 있고 검색 시 highlight 가 생성되면
**Then** 응답 highlight 에서 `<mark>` 외 모든 태그·속성이 제거되어 있다 (REQ-SEARCH-002 sanitize).

**Given** `search_synonym` 의 C/U/D 가 발생하면
**When**
**Then** SPEC-CMS-005 `audit_log` 에 자동 적재된다 (AOP 연동).

---

### F-3 다국어

**Given** locale=ko 와 locale=en 으로 모두 인덱싱된 `page` (`tsv_ko`, `tsv_en`) 가 존재하고
**When** `GET /api/v1/search?q=청년&locale=ko` 를 호출하면
**Then** 시스템은 `to_tsvector('simple', ...)` 기반 매칭 + `pg_trgm` 보완으로 결과를 반환한다.

**And When** `GET /api/v1/search?q=youth&locale=en` 을 호출하면
**Then** 시스템은 `to_tsvector('english', ...)` 기반 stemming 매칭으로 결과를 반환한다 (예: youth/youthful 매칭).

**And When** `Accept-Language: en` 헤더 + `?locale=ko` 파라미터를 동시 전달하면
**Then** 파라미터 우선 원칙에 따라 `locale=ko` 로 검색된다.

**And When** `?locale=fr` (지원하지 않는 locale) 으로 호출하면
**Then** 400 Bad Request + 에러 코드 `SEARCH_LOCALE_UNSUPPORTED` 가 반환된다.

---

### F-4 관측성 + 데이터 거버넌스 (SPEC-CMS-009 통합)

**Given** Step 1 마이그레이션 + 시드 INSERT 가 완료된 상태에서
**When** `GET /api/v1/governance/dictionary?table_name=search_log` 를 호출하면
**Then** `search_log` 의 모든 컬럼이 SPEC-CMS-009 `data_dictionary` 에 등록되어 있고 `query`, `ip_hash` 컬럼은 `is_pii=true` 로 표시된다.

**And When** `?table_name=search_popular_cache` 로 조회하면 `data_domain='STATISTICS'` 로 등록되어 있다.

**And When** `?table_name=search_synonym` 으로 조회하면 `data_domain='MASTER'` 로 등록되어 있다.

**And When** SPEC-CMS-009 `data_quality_rule` 시드(0건 검색 비율 30% 임계)가 등록된 상태에서 `search_log` 의 `result_count=0` 행 비율이 35% 가 되면
**Then** `data_quality_report.violation=TRUE, severity='WARN'` 이 적재되고 운영자 알림 큐에 push 된다.

---

## G. Quality Gates

본 SPEC 의 Quality Gate 는 SPEC-CMS-001 §17.4 공통 게이트 + 본 SPEC 고유 게이트로 구성된다.

### G-1 공통 (SPEC-CMS-001 §17.4)

- **QG-COMMON-1 (QUR-004)**: 시험 운영 기간 동안 결함 발생률 5% 미만.
- **QG-COMMON-2 (QUR-004)**: P0 결함 지속시간 1시간 이내 해결.

### G-2 본 SPEC 고유

- **QG-010-1 의존 인프라 검증**: `SearchInfraValidator` 부팅 검증이 PASS — `bbs_post.search_vector`, `page.tsv_ko`, `page.tsv_en`, `safety_incidents.search_vector`, `idx_faq_question_trgm`, `idx_qna_title_trgm`, `media_asset` 인덱스가 `pg_indexes` 카탈로그에 모두 존재해야 한다 (ASSUM-S-01).
- **QG-010-2 통합 검색 SLA**: 6개 도메인 통합 검색이 10만 문서 환경에서 5회 연속 p95 < 500ms 를 충족 (REQ-SEARCH-010).
- **QG-010-3 자동완성 SLA**: 자동완성이 5회 연속 p95 < 100ms 를 충족.
- **QG-010-4 인기 검색어 SLA**: 인기 검색어 조회가 5회 연속 p95 < 50ms 를 충족, 일/주/월 배치가 PER-003 SLA(10분 / 1시간) 를 5회 연속 충족.
- **QG-010-5 비공개 콘텐츠 보안**: REQ-SEARCH-003 silent 제외가 모든 비공개 시나리오(QnA `is_secret=true`, page `visibility=PRIVATE`, safety 비공개)에서 정확히 동작 (정보 누출 0건).
- **QG-010-6 XSS sanitize**: REQ-SEARCH-002 hightlight sanitize 가 OWASP XSS Filter Evasion Cheat Sheet 의 핵심 페이로드 20종을 모두 차단.
- **QG-010-7 코드 커버리지**: SPEC-CMS-001 §17.4 와 동일하게 신규 작성 코드의 테스트 커버리지 85% 이상.
- **QG-010-8 의존 SPEC 무회귀**: 본 SPEC 구현 후 SPEC-CMS-003 (게시판 검색), SPEC-CMS-004 (콘텐츠 검색), SPEC-CMS-009 (batch_execution_log/retention_policy) 의 기존 acceptance 시나리오가 모두 GREEN 유지.
- **QG-010-9 데이터 거버넌스**: 신규 3개 테이블이 SPEC-CMS-009 `data_dictionary` 에 자기 등록되어 있고, `search_log.query`/`ip_hash` 가 `is_pii=true` 로 등록되어 있어야 한다.

### G-3 검증 시나리오 (Test Scenarios)

| ID | 대상 | 검증 방법 | Tool |
|----|------|----------|------|
| TS-001 | 통합 검색 6개 도메인 매칭 + facets | Testcontainers PG 16 + 도메인별 fixture | RestAssured |
| TS-002 | 도메인 가중치 정렬 (boards>contents>publication>policies>safety>media) | rank 동률 fixture로 가중치만 검증 | JUnit 5 |
| TS-003 | ts_headline + sanitize (XSS 페이로드 20종) | 악의적 fixture + jsoup parser 결과 비교 | JUnit 5 |
| TS-004 | 비공개 콘텐츠 silent 제외 (비로그인/본인/타인/ADMIN 4개 분기) | Spring Security Test + fixture | RestAssured |
| TS-005 | size > 50 거부 / domain invalid 거부 / locale 미지원 거부 | 파라미터 validation 검증 | MockMvc |
| TS-006 | 자동완성 인기검색어 + 콘텐츠 제목 통합 정렬 | search_popular_cache + bbs_post fixture | JUnit 5 |
| TS-007 | 자동완성 1자 prefix 빈 응답 / 51자 prefix 거부 | 경계값 검증 | MockMvc |
| TS-008 | 자동완성 cache 미존재 폴백 (콘텐츠 제목 단독) | search_popular_cache 비움 | JUnit 5 |
| TS-009 | 인기 검색어 캐시 미스 폴백 (직전 period_date) | period_date 부재 fixture | JUnit 5 |
| TS-010 | PopularQueryAggregateDailyJob (HAVING >=3, result_count>0 필터) | search_log fixture (3건 미만 + 0건 검색 포함) | Testcontainers |
| TS-011 | PopularQueryAggregateWeeklyJob / MonthlyJob | 시간 fixture (Clock Bean) | Testcontainers |
| TS-012 | 배치 재시도 3회 후 CRITICAL (SPEC-CMS-009 통합) | DataSource SpyBean failure injection | Spring Boot Test |
| TS-013 | 검색 로그 비동기 적재 + 응답 시간 영향 < 10ms | JMeter wallclock 측정 + DB count 검증 | JMeter |
| TS-014 | 클릭 추적 30분 윈도우 + session_id 매칭 검증 | 시간 fixture + 다른 session_id | RestAssured |
| TS-015 | 검색 로그 retention 6개월 자동 DELETE | SPEC-CMS-009 retention 배치 통합 | Testcontainers |
| TS-016 | 동의어 OR 확장 + audit_log AOP 적재 | search_synonym fixture + SpyBean | Spring Boot Test |
| TS-017 | 동의어 self/duplicate/locale 거부 | DB CHECK + UNIQUE 제약 검증 | MockMvc |
| TS-018 | 동의어 토큰 20개 절단 (RISK-S-05) | 25개 synonym fixture | JUnit 5 |
| TS-019 | 검색 API 권한 (PUBLIC 통합검색/자동완성/인기/클릭, ADMIN 동의어/통계) | Spring Security Test | RestAssured |
| TS-020 | SQL injection 무력화 (prepared statement) | `'; DROP TABLE; --` 페이로드 fixture | JUnit 5 |
| TS-021 | 다국어 ko/en 분기 (simple vs english parser) | tsv_ko / tsv_en fixture | JUnit 5 |
| TS-022 | 통합 검색 부하 테스트 10만 문서 + 100 thread × 10 iter | JMeter | 수동 검수 + CI |
| TS-023 | 자동완성 부하 테스트 100 thread × 10 iter | JMeter | 수동 검수 |
| TS-024 | SearchInfraValidator 부팅 검증 (인덱스 누락 시 ApplicationFailedEvent) | pg_indexes 조작 fixture | Spring Boot Test |
| TS-025 | 의존 SPEC 무회귀 (SPEC-CMS-003/004/009 acceptance 재실행) | 전체 통합 테스트 수트 | CI |
| TS-026 | data_dictionary 자기 등록 (3개 테이블 × 평균 12 컬럼) | SPEC-CMS-009 governance API 통합 | Spring Boot Test |

---

## H. Definition of Done

본 SPEC 구현 완료 조건:

- [ ] Step 1 (마이그레이션 V20260507_001~004 + 도메인 모델 + Repository + SearchInfraValidator) 완료
- [ ] Step 2 (REST API 9개 + 통합검색·자동완성·인기검색어·동의어 서비스 + 비동기 로그 적재) 완료
- [ ] Step 3 (3개 인기검색어 배치 + retention_policy hook + 데이터 품질 룰 등록) 완료
- [ ] Step 4 (Frontend 4개 view: SearchView, SearchAutocomplete, PopularQueriesWidget, SynonymManagementView) 완료
- [ ] Step 5 (SPEC-CMS-008 대시보드 검색 위젯 통합) 완료
- [ ] acceptance.md A~F 모든 G/W/T 시나리오 GREEN (TS-001 ~ TS-026)
- [ ] Quality Gate G-1 공통 + G-2 본 SPEC 고유 9개 모두 PASS
- [ ] 신규 코드 테스트 커버리지 ≥ 85%
- [ ] PostgreSQL FTS 단일 스택 결정 준수 (ElasticSearch / Mecab-ko / 시맨틱 검색은 비범위)
- [ ] SPEC-CMS-003/004/006/MEDIA-001 인덱스 자산을 입력으로 사용 (재정의 0건, ASSUM-S-01 부팅 검증 PASS)
- [ ] SPEC-CMS-009 `batch_execution_log` 통합 (job_group='SEARCH') 및 `retention_policy` 시드 2건 자동 실행 검증
- [ ] SPEC-CMS-009 `data_dictionary` 자기 등록 (신규 3개 테이블 × 평균 12 컬럼 ≈ 36행, `search_log.query`/`ip_hash` is_pii=true)
- [ ] OWASP XSS Filter Evasion Cheat Sheet 핵심 20종 sanitize 통과
- [ ] 변경 이력 (spec.md §12) v0.1 기록
- [ ] SPEC-CMS-001 §16.1 트리에 `SPEC-CMS-010 통합 검색 [v0.1 — Draft, 2026-05-07]` 갱신 권고 (작업 범위 외 별도 트랜잭션)
