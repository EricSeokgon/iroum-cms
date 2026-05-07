# iroum-cms 핵심 데이터 흐름 시나리오

> 최종 업데이트: 2026-05-07
> 근거 자료: Explore 에이전트 인벤토리 (2026-05-07)

이 문서는 iroum-cms의 5가지 핵심 데이터 흐름 시나리오를 Mermaid 시퀀스 다이어그램과 함께 상세히 설명합니다.

---

## 시나리오 1: 사용자 로그인 및 JWT 발급

### 흐름 다이어그램

```mermaid
sequenceDiagram
    actor Browser
    participant SecurityFilter as SecurityConfig<br/>(JWT Filter)
    participant AuthController as AuthController<br/>/api/v1/auth/login
    participant AuthService as AuthService
    participant UserMapper as UserMapper<br/>(MyBatis)
    participant JwtProvider as JwtProvider
    participant AuditAOP as AuditLogAspect<br/>(AOP)
    participant LoginHistMapper as LoginHistoryMapper
    participant PG as PostgreSQL<br/>(users, login_history)

    Browser->>SecurityFilter: POST /api/v1/auth/login<br/>{username, password}
    Note over SecurityFilter: PUBLIC 엔드포인트<br/>인증 필터 통과
    SecurityFilter->>AuthController: 요청 전달
    AuthController->>AuthService: login(username, password)
    AuditAOP-->>AuthService: @AuditLog 인터셉트 (호출 전)
    AuthService->>UserMapper: findByUsername(username)
    UserMapper->>PG: SELECT * FROM users WHERE username = ?
    PG-->>UserMapper: User 엔티티
    UserMapper-->>AuthService: User 반환
    AuthService->>AuthService: BCrypt.matches(password, user.passwordHash)
    Note over AuthService: 비밀번호 검증 실패 시<br/>AuthException 반환
    AuthService->>JwtProvider: issueAccessToken(user)
    JwtProvider-->>AuthService: accessToken (jjwt 0.12.7)
    AuthService->>JwtProvider: issueRefreshToken(user)
    JwtProvider-->>AuthService: refreshToken
    AuthService->>LoginHistMapper: insert(LoginHistory)
    LoginHistMapper->>PG: INSERT INTO login_history
    AuditAOP-->>AuthService: @AuditLog 인터셉트 (호출 후)<br/>audit_log INSERT
    AuthService-->>AuthController: {accessToken, refreshToken, user}
    AuthController-->>Browser: 200 OK<br/>{accessToken, refreshToken}
```

### 설명

사용자가 로그인 요청을 보내면 `SecurityConfig`의 JWT 필터가 PUBLIC 엔드포인트임을 확인하고 인증 검사 없이 `AuthController`로 전달합니다. `AuthService`는 `UserMapper`를 통해 PostgreSQL의 `users` 테이블에서 사용자를 조회하고, BCrypt로 비밀번호를 검증합니다. 검증 성공 시 jjwt 0.12.7을 사용하여 액세스 토큰과 리프레시 토큰을 발급합니다.

`AuditLogAspect`가 `@AuditLog` AOP를 통해 AuthService 호출 전·후에 자동으로 `audit_log` 테이블에 로그인 이벤트를 적재합니다. 이 횡단 관심사는 Service 코드를 수정하지 않고 어노테이션만으로 동작합니다. 로그인 이력은 별도로 `login_history` 테이블에도 기록됩니다.

**관련 도메인**: auth, audit
**횡단 관심사**: `AuditLogAspect` (@AuditLog AOP), `SecurityConfig` (PUBLIC 허용 목록)

---

## 시나리오 2: 통합 검색 및 클릭 추적

### 흐름 다이어그램

```mermaid
sequenceDiagram
    actor Browser
    participant SearchCtrl as SearchController<br/>/api/v1/search
    participant SearchSvc as SearchService
    participant SynonymSvc as SynonymService
    participant SynonymMapper as SearchSynonymMapper
    participant UnifiedMapper as UnifiedSearchMapper<br/>(UNION ALL)
    participant AsyncSvc as SearchLogAsyncService<br/>(@Async)
    participant LogMapper as SearchLogMapper
    participant PG as PostgreSQL<br/>(6 domains + search_log)

    Browser->>SearchCtrl: GET /api/v1/search?q=안전관리
    SearchCtrl->>SearchSvc: search(query, params)
    SearchSvc->>SynonymSvc: expandQuery("안전관리")
    SynonymSvc->>SynonymMapper: findActiveByTerm("안전관리")
    SynonymMapper->>PG: SELECT synonym FROM search_synonym WHERE term = ?
    PG-->>SynonymMapper: ["안전", "사고예방", "위험관리"]
    SynonymMapper-->>SynonymSvc: 동의어 목록
    SynonymSvc-->>SearchSvc: expandedQuery = "안전관리 OR 안전 OR 사고예방 OR 위험관리"
    SearchSvc->>UnifiedMapper: searchUnified(expandedQuery, page, size)
    UnifiedMapper->>PG: UNION ALL<br/>SELECT FROM bbs_post WHERE search_vector @@ ?<br/>UNION ALL SELECT FROM content_page WHERE tsv_ko @@ ?<br/>UNION ALL SELECT FROM policy WHERE search_vector @@ ?<br/>UNION ALL SELECT FROM safety_incident WHERE search_vector @@ ?<br/>UNION ALL SELECT FROM media_asset WHERE search_vector @@ ?<br/>UNION ALL SELECT FROM publication WHERE search_vector @@ ?
    PG-->>UnifiedMapper: 통합 검색 결과 (ts_rank 정렬)
    UnifiedMapper-->>SearchSvc: SearchResult[]
    SearchSvc->>SearchSvc: sanitizeHighlight(results)<br/>(jsoup XSS 방어)
    SearchSvc->>AsyncSvc: logAsync(query, results) [비동기]
    Note over AsyncSvc: searchLogExecutor<br/>별도 스레드 풀
    AsyncSvc->>LogMapper: insert(SearchLog)
    LogMapper->>PG: INSERT INTO search_log
    SearchSvc-->>SearchCtrl: SearchResponse
    SearchCtrl-->>Browser: 200 OK { results, total, ... }

    Browser->>SearchCtrl: POST /api/v1/search/click<br/>{searchLogId, resultId, position}
    SearchCtrl->>SearchSvc: updateClickInfo(searchLogId, resultId)
    SearchSvc->>LogMapper: updateClickInfo(id, clickedDocId)
    LogMapper->>PG: UPDATE search_log SET clicked_doc_id = ?
    SearchCtrl-->>Browser: 200 OK
```

### 설명

검색 요청이 들어오면 `SearchService`는 먼저 `SynonymService`를 통해 동의어를 확장합니다. 예를 들어 "안전관리"를 검색하면 `search_synonym` 테이블에서 관련 동의어를 조회하여 쿼리를 풍부화합니다. 확장된 쿼리는 `UnifiedSearchMapper`로 전달되어 PostgreSQL의 UNION ALL 쿼리를 실행합니다. 이 쿼리는 6개 도메인 테이블(게시글, 콘텐츠 페이지, 정책, 사고사례, 미디어, 발간자료)을 한 번에 검색합니다.

검색 결과는 jsoup을 사용하여 하이라이트 마크업의 XSS를 방어한 후 클라이언트에 반환됩니다. 동시에 `@Async` 어노테이션을 통해 `searchLogExecutor` 전용 스레드 풀에서 검색 로그가 비동기로 적재됩니다. 이를 통해 로그 적재가 검색 응답 시간에 영향을 주지 않습니다. 사용자가 특정 결과를 클릭하면 별도 API 호출로 클릭 추적이 이루어집니다.

**관련 도메인**: search, board, content, policy, safety, media (읽기 전용)
**횡단 관심사**: `AsyncConfig.searchLogExecutor` (비동기 처리), `SecurityConfig` (PUBLIC 엔드포인트)

---

## 시나리오 3: 게시글 작성 및 검색 인덱스 자동 갱신

### 흐름 다이어그램

```mermaid
sequenceDiagram
    actor Browser
    participant SecurityFilter as SecurityConfig<br/>(JWT 검증)
    participant PostCtrl as PostController<br/>/api/v1/boards/{id}/posts
    participant PostSvc as PostService
    participant PostMapper as BbsPostMapper
    participant AuditAOP as AuditLogAspect<br/>(AOP)
    participant PG as PostgreSQL<br/>(bbs_post)
    participant Trigger as DB 트리거<br/>bbs_post_search_vector_update

    Browser->>SecurityFilter: POST /api/v1/boards/{boardId}/posts<br/>Authorization: Bearer {accessToken}
    SecurityFilter->>SecurityFilter: JWT 검증 + @PreAuthorize('ROLE_USER') 확인
    SecurityFilter->>PostCtrl: 인가된 요청 전달
    PostCtrl->>PostSvc: createPost(boardId, CreatePostRequest, userDetails)
    AuditAOP-->>PostSvc: @AuditLog 인터셉트 (호출 전)<br/>action = POST_CREATE
    PostSvc->>PostSvc: 유효성 검증, BbsPost 엔티티 구성
    PostSvc->>PostMapper: insert(BbsPost)
    PostMapper->>PG: INSERT INTO bbs_post<br/>(title, content, board_id, user_id, ...)
    PG->>Trigger: AFTER INSERT 트리거 실행<br/>bbs_post_search_vector_update
    Trigger->>PG: UPDATE bbs_post SET search_vector =<br/>to_tsvector('korean', title || ' ' || content)<br/>WHERE id = NEW.id
    Note over PG: GIN 인덱스 자동 갱신<br/>다음 검색에서 즉시 검색 가능
    PG-->>PostMapper: 삽입 성공 (id 반환)
    AuditAOP-->>PostSvc: @AuditLog 인터셉트 (호출 후)<br/>audit_log INSERT
    PostSvc-->>PostCtrl: BbsPost (생성된 게시글)
    PostCtrl-->>Browser: 201 Created<br/>{id, title, content, createdAt, ...}

    Note over Browser, PG: 이후 검색 시 즉시 인덱싱된 게시글 검색 가능
    Browser->>PG: GET /api/v1/search?q=게시글제목
    Note over PG: bbs_post.search_vector에<br/>GIN 인덱스 조회 가능
```

### 설명

게시글 작성 요청은 JWT 필터에서 액세스 토큰을 검증하고, `@PreAuthorize`로 사용자 역할을 확인한 후 `PostController`에 도달합니다. `PostService`는 게시글 엔티티를 구성하여 `BbsPostMapper`를 통해 PostgreSQL에 삽입합니다.

가장 중요한 점은 PostgreSQL DB 트리거 `bbs_post_search_vector_update`가 INSERT 이후 자동으로 실행된다는 것입니다. 이 트리거는 `to_tsvector()` 함수를 사용하여 게시글 제목과 본문에서 한국어 전문 검색 벡터를 생성하고 `search_vector` 컬럼을 갱신합니다. GIN 인덱스가 이 컬럼에 걸려 있으므로, 게시글 저장 직후부터 통합 검색에서 해당 게시글을 찾을 수 있습니다. 별도의 인덱싱 잡이나 Elasticsearch 동기화 없이 DB 레벨에서 검색 인덱스가 자동 관리됩니다.

`AuditLogAspect`는 `PostService.createPost()` 호출 전·후에 POST_CREATE 이벤트를 `audit_log`에 기록합니다.

**관련 도메인**: board, auth, audit, search (간접 — 다음 검색에서 즉시 반영)
**횡단 관심사**: `AuditLogAspect` (@AuditLog), `SecurityConfig` (JWT + @PreAuthorize), PostgreSQL 트리거

---

## 시나리오 4: 데이터 보존 정책 자동 적용

### 흐름 다이어그램

```mermaid
sequenceDiagram
    participant Scheduler as Spring @Scheduled
    participant RetentionJob as SearchLogRetentionJob<br/>매일 05:35
    participant BatchSvc as BatchExecutionLogService
    participant RetentionSvc as RetentionPolicyService
    participant RetentionMapper as RetentionPolicyMapper<br/>(governance)
    participant LogMapper as SearchLogMapper<br/>(search)
    participant PG as PostgreSQL<br/>(retention_policy, search_log, batch_execution_log)
    participant DailyJob as PopularQueryAggregateDailyJob<br/>매일 04:30
    participant PopularMapper as SearchPopularCacheMapper

    Note over Scheduler: 매일 04:30 KST
    Scheduler->>DailyJob: trigger()
    DailyJob->>BatchSvc: start("POPULAR_DAILY")
    BatchSvc->>PG: INSERT INTO batch_execution_log {status: RUNNING}
    DailyJob->>LogMapper: aggregateDaily(yesterday)
    LogMapper->>PG: SELECT term, COUNT(*) FROM search_log<br/>WHERE created_at >= yesterday<br/>GROUP BY term ORDER BY COUNT DESC
    PG-->>LogMapper: 집계 결과
    DailyJob->>PopularMapper: upsert(dailyStats)
    PopularMapper->>PG: INSERT INTO popular_search_cache ON CONFLICT UPDATE
    DailyJob->>BatchSvc: success("POPULAR_DAILY")
    BatchSvc->>PG: UPDATE batch_execution_log SET status = SUCCESS

    Note over Scheduler: 매일 05:35 KST
    Scheduler->>RetentionJob: trigger()
    RetentionJob->>BatchSvc: start("SEARCH_LOG_RETENTION")
    BatchSvc->>PG: INSERT INTO batch_execution_log {status: RUNNING}
    RetentionJob->>RetentionSvc: getPolicy("search_log")
    RetentionSvc->>RetentionMapper: findByTableName("search_log")
    RetentionMapper->>PG: SELECT * FROM retention_policy WHERE table_name = 'search_log'
    PG-->>RetentionMapper: RetentionPolicy {retentionDays: 180}
    RetentionMapper-->>RetentionSvc: 보존 정책 (180일)
    RetentionSvc-->>RetentionJob: retentionDays = 180
    RetentionJob->>LogMapper: deleteOlderThan(cutoffDate)
    LogMapper->>PG: DELETE FROM search_log<br/>WHERE created_at < (NOW() - INTERVAL '180 days')
    PG-->>LogMapper: 삭제 건수
    RetentionJob->>BatchSvc: success("SEARCH_LOG_RETENTION", deletedCount)
    BatchSvc->>PG: UPDATE batch_execution_log SET status = SUCCESS
```

### 설명

데이터 보존 정책 시나리오는 두 단계로 구성됩니다.

첫째, 매일 04:30에 `PopularQueryAggregateDailyJob`이 실행되어 전날의 검색 로그를 집계합니다. `search_log` 테이블에서 검색어별 빈도를 집계하여 `popular_search_cache` 테이블을 갱신(upsert)합니다. 이 결과가 `/api/v1/search/popular` 엔드포인트에서 반환되는 인기 검색어 데이터입니다.

둘째, 매일 05:35에 `SearchLogRetentionJob`이 실행되어 오래된 검색 로그를 삭제합니다. `governance` 도메인의 `retention_policy` 테이블에서 `search_log` 테이블의 보존 기간(예: 180일)을 조회하고, 해당 기간이 지난 레코드를 일괄 삭제합니다. 이 패턴은 governance 도메인의 단일 테이블(`retention_policy`)이 여러 도메인의 데이터 보존 기간을 중앙에서 통제하는 설계를 보여줍니다.

모든 배치 실행은 `BatchExecutionLogService`를 통해 `batch_execution_log` 테이블에 시작·완료 상태를 기록하며, 이 기록이 dashboard KPI 위젯의 거버넌스 통계로 집계됩니다.

**관련 도메인**: search, governance, dashboard (통계 집계)
**횡단 관심사**: Spring `@Scheduled`, `BatchExecutionLogService` (모든 배치 공통)

---

## 시나리오 5: KPI 대시보드 위젯 데이터 조회

### 흐름 다이어그램

```mermaid
sequenceDiagram
    actor Browser
    participant SecurityFilter as SecurityConfig<br/>(JWT 검증)
    participant WidgetCtrl as DashboardWidgetController<br/>/api/v1/dashboard/widgets/{id}/data
    participant WidgetSvc as DashboardWidgetService
    participant CacheMgr as CacheConfig<br/>(Caffeine TTL 5분)
    participant KpiSvc as KpiAggregationService
    participant SystemMapper as AccessStatMapper<br/>(system)
    participant PolicyMapper as PolicyStatsMapper<br/>(policy)
    participant SafetyMapper as SafetyStatsMapper<br/>(safety)
    participant GovMapper as BatchExecutionLogMapper<br/>(governance)
    participant PG as PostgreSQL<br/>(4 domains)

    Browser->>SecurityFilter: GET /api/v1/dashboard/widgets/{widgetId}/data<br/>Authorization: Bearer {accessToken}
    SecurityFilter->>SecurityFilter: JWT 검증 + 대시보드 접근 권한 확인
    SecurityFilter->>WidgetCtrl: 인가된 요청 전달
    WidgetCtrl->>WidgetSvc: getWidgetData(widgetId, userId)
    WidgetSvc->>CacheMgr: cache.get("widget:" + widgetId)
    alt 캐시 히트 (TTL 5분 이내)
        CacheMgr-->>WidgetSvc: 캐시된 KPI 데이터
    else 캐시 미스
        WidgetSvc->>KpiSvc: compute(widgetConfig)
        Note over KpiSvc: 위젯 유형에 따라<br/>4개 도메인 병렬 집계
        KpiSvc->>SystemMapper: getAccessStats(period)
        SystemMapper->>PG: SELECT * FROM access_stat WHERE date >= ?
        PG-->>SystemMapper: 일별 접속 통계
        KpiSvc->>PolicyMapper: getMatchStats(period)
        PolicyMapper->>PG: SELECT * FROM policy_match_stats WHERE date >= ?
        PG-->>PolicyMapper: 정책 매칭 통계
        KpiSvc->>SafetyMapper: getSafetyStats(period)
        SafetyMapper->>PG: SELECT * FROM safety_stats WHERE date >= ?
        PG-->>SafetyMapper: 안전 통계
        KpiSvc->>GovMapper: getBatchStats(period)
        GovMapper->>PG: SELECT * FROM batch_execution_log WHERE started_at >= ?
        PG-->>GovMapper: 배치 실행 통계
        KpiSvc-->>WidgetSvc: KpiData (집계 완료)
        WidgetSvc->>CacheMgr: cache.put("widget:" + widgetId, kpiData, TTL=5분)
    end
    WidgetSvc-->>WidgetCtrl: WidgetDataResponse (ECharts 포맷)
    WidgetCtrl-->>Browser: 200 OK<br/>{series, xAxis, yAxis, ...} (ECharts 데이터)
    Note over Browser: vue-echarts 5.5.1로<br/>차트 렌더링
```

### 설명

대시보드 위젯 데이터 조회는 Caffeine 캐시를 중심으로 동작합니다. 위젯 데이터 요청이 들어오면 `DashboardWidgetService`는 먼저 Caffeine 캐시에서 캐시 히트 여부를 확인합니다. TTL 5분 이내에 동일한 위젯 데이터가 요청된 경우 캐시에서 즉시 반환하여 DB 쿼리를 완전히 생략합니다.

캐시 미스 시 `KpiAggregationService`가 위젯 설정에 따라 4개 도메인(system, policy, safety, governance)의 통계 테이블을 집계합니다. 각 도메인의 Mapper는 해당 도메인 통계 테이블에서 데이터를 조회하고, 집계 결과는 ECharts 데이터 포맷(`series`, `xAxis`, `yAxis`)으로 변환됩니다. 이 포맷은 프론트엔드의 vue-echarts 7.0.3이 직접 소비합니다.

응답 데이터는 Caffeine 캐시에 저장(TTL 5분)되어 다음 5분 동안 동일 요청에 대해 DB 조회 없이 응답합니다. 이 캐싱 전략은 대시보드가 여러 관리자에 의해 동시에 조회될 때 PostgreSQL 부하를 크게 줄입니다.

**관련 도메인**: dashboard, system, policy, safety, governance
**횡단 관심사**: `CacheConfig` (Caffeine TTL 5분), `SecurityConfig` (JWT + 역할 확인)

---

## 데이터 흐름 요약

```mermaid
graph TB
    subgraph Flows["핵심 데이터 흐름"]
        F1["시나리오 1\n로그인 + JWT\nauth → audit"]
        F2["시나리오 2\n통합 검색 + 클릭\nsearch → 6 domains"]
        F3["시나리오 3\n게시글 + 검색 인덱스\nboard → DB 트리거"]
        F4["시나리오 4\n보존 정책 배치\ngovernance → search"]
        F5["시나리오 5\nKPI 대시보드\ndashboard → 4 domains"]
    end

    subgraph CrossCutting["모든 시나리오 공통 횡단 관심사"]
        JWT["SecurityConfig\nJWT 필터 + @PreAuthorize"]
        Audit["AuditLogAspect\n@AuditLog AOP"]
        Cache["CacheConfig\nCaffeine TTL"]
        Async["AsyncConfig\nsearchLogExecutor"]
    end

    F1 -.-> JWT
    F1 -.-> Audit
    F2 -.-> JWT
    F2 -.-> Async
    F3 -.-> JWT
    F3 -.-> Audit
    F4 -.-> Audit
    F5 -.-> JWT
    F5 -.-> Cache
```
