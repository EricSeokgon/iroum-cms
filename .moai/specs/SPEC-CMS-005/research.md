# SPEC-CMS-005 Research Notes

> 본 문서는 Bundle D(통계·로그·시스템관리) 상세 SPEC 작성 시 도출한 8개 결정 사항을 정리한다. 각 항목은 Decision / Rationale / Alternatives / 1차 적용 / 후속 검토 형식이다.

---

## 1. 감사로그 적재 방식 — 동기 vs 비동기

### 결정 (1차)

**비동기 적재**(`@Async` + `auditExecutor` 스레드 풀, core=2 max=8 queue=1000) + RetryTemplate 3회 재시도 + 실패 시 `audit_fallback` 큐(파일 또는 별도 테이블).

### 근거

- 감사로그 적재가 도메인 트랜잭션을 차단하면 응답 지연이 누적되어 SLA(P95 200ms) 위협
- audit_log INSERT 자체가 디스크 I/O + 파티션 라우팅 비용이 있어 5~20ms 소요
- 비동기는 도메인 트랜잭션과 분리되어 적재 실패가 비즈니스 롤백을 유발하지 않음 (감사 누락 시에도 비즈니스는 진행)

### 트레이드오프

- 비동기 큐 오버플로우 시 감사 누락 가능 → fallback 큐로 보완
- 트랜잭션 후 실패 = 감사 누락 위험 → CompletableFuture + 재시도 + 모니터링으로 완화
- 동기 방식은 감사 무결성 100% 보장이지만 성능 손실이 큼

### 대안

- **동기 적재**: 강한 무결성, 성능 저하 (1차 제외)
- **이벤트 소싱 (Kafka/Outbox)**: 가장 견고하나 인프라 추가 (1차 제외, 후속 SPEC)
- **CDC 기반 적재**: PG WAL → Debezium → audit topic (운영 복잡도 큼, 후속)

### 1차 적용

비동기 + 재시도 + fallback. 큐 크기는 Prometheus `audit_executor_queue_size` 메트릭으로 노출.

### 후속

트래픽 증가 또는 다중 노드 시 Outbox 패턴 또는 Kafka 도입 검토.

---

## 2. APPEND-ONLY 강제 — DB 트리거 vs 애플리케이션 레벨

### 결정 (1차)

**PostgreSQL 트리거**(`fn_audit_log_immutable` BEFORE UPDATE/DELETE) 1차 + 애플리케이션 레벨 MyBatis Mapper 정적 검사 2차.

### 근거

- 트리거는 SQL 클라이언트(애플리케이션, DBA, 백업 도구) 모두에 동일 적용 → 우회 불가
- 애플리케이션 레벨만 보호 시 DB 직접 접속(psql) 으로 우회 가능
- 감사로그 위변조는 컴플라이언스 위반(공공기관 감사로그 보존 의무)

### 트레이드오프

- 트리거는 정상 운영자도 행을 수정할 수 없음 → 운영 매뉴얼에 "수정 시 별도 행 INSERT" 명시
- 트리거 실수로 비활성화 시 무결성 위험 → DDL 변경 감사로그 + DBA 권한 분리

### 대안

- 애플리케이션 레벨만: MyBatis Mapper 에서 INSERT 만 허용 (취약)
- WORM 스토리지: S3 Object Lock (1차는 PG 만, 콜드 이관 시 적용)
- Hash chain (블록체인 스타일): 과잉 (후속)

### 1차 적용

트리거 + 별도 SUPERUSER 만 트리거 비활성화 가능 + DBA 권한 분리.

### 후속

S3 Object Lock + WORM 콜드 스토리지(SPEC-CMS-008+).

---

## 3. 통계 집계 — 실시간 vs 배치

### 결정 (1차)

**배치**(시간/일/월 cron) + 사전 집계 테이블(`access_stat_daily`, `access_stat_monthly`) + 60초 Caffeine 캐시.

### 근거

- 1차 운영 환경은 단일 노드, 일/월 KPI 가 주 사용 시나리오
- 실시간 지표는 운영자에게 큰 가치 없음 (대시보드는 분 단위 갱신으로 충분)
- 배치는 access_log 원본을 GROUP BY 로 집계 → 단순, 안정적
- 사전 집계 테이블이 통계 API p95 < 300ms 보장

### 트레이드오프

- 당일 통계는 배치 전까지 미반영 → "오늘 KPI"는 실시간 access_log COUNT (제한된 윈도우)
- 배치 실패 시 KPI 결손 → 3회 재시도 + 수동 재집계 API

### 대안

- **TimescaleDB**: 실시간 + 압축 + 자동 집계 hypertable (1차 제외, 후속 검토)
- **ClickHouse**: 분석 전용, 실시간 (인프라 추가, 후속)
- **Materialized View**: REFRESH 비용 (배치와 유사, 트리거 부담)

### 1차 적용

Spring `@Scheduled` + UPSERT(`ON CONFLICT`).

### 후속

월간 access_log 100M 행 초과 시 TimescaleDB 평가.

---

## 4. PostgreSQL 파티셔닝 vs 외부 보관

### 결정 (1차)

**PG 월별 RANGE PARTITION** + 6개월 후 DETACH → PG_DUMP → S3 Glacier (수동 절차 1차).

### 근거

- audit_log 와 access_log 는 시간 기반 INSERT 패턴 → 월별 RANGE 가 자연스러움
- 파티션 단위 DROP 으로 보존 정책 적용 단순
- 6개월 핫(조회 빈도 높음) + 5년 콜드(법정 보존) 분리로 비용 최적화
- pg_partman 또는 자체 SQL 로 자동 파티션 생성

### 트레이드오프

- 파티션 자동 생성 누락 시 INSERT 실패 → 매월 25일 사전 생성 + 모니터링
- 콜드 이관은 1차 수동 → 운영 매뉴얼 의존
- 외부 DB 분리는 운영 복잡도 증가

### 대안

- 별도 PG instance(audit-db): 완전 격리, 인프라 추가
- TimescaleDB hypertable: 자동 chunk + 압축 (후속)
- 외부 로그 시스템(ELK): 검색은 강력하나 컴플라이언스 부담

### 1차 적용

PG 16 PARTITION BY RANGE + `pg_partman` 또는 자체 cron, 콜드 이관은 운영 매뉴얼.

### 후속

자동 콜드 이관 SPEC + S3 Object Lock + 보존 알림.

---

## 5. 공통코드 캐시 — Caffeine vs Redis

### 결정 (1차)

**Caffeine** 인메모리 캐시 (Spring Cache 추상화) + TTL 1시간 + 변경 시 즉시 evict.

### 근거

- 1차는 단일 백엔드 노드 → 분산 캐시 불필요
- Caffeine 은 마이크로초 단위 응답, GC 친화적, Bloom filter 등 기능 풍부
- 코드 마스터는 변경 빈도 낮음(일 단위) → TTL 1시간 충분
- Redis 는 추가 인프라 + 네트워크 hop 비용

### 트레이드오프

- 멀티노드 시 노드 간 캐시 불일치 → 1차 단일 노드 한정 명시
- 인스턴스 재시작 시 캐시 cold start → 1시간 TTL 로 재구축 부담 작음

### 대안

- **Redis 단일**: 멀티노드 친화, 추가 인프라 (후속)
- **Hazelcast**: in-process 분산 캐시 (Java 친화, 1차 과잉)
- **DB 직접 조회**: 단순하나 캐시 미사용

### 1차 적용

Caffeine + Spring `@Cacheable`/`@CacheEvict`.

### 후속

멀티노드 전환 시 Redis 도입 + Spring Cache + Redis pub/sub 캐시 무효화.

---

## 6. 관측성 스택 — Prometheus vs ELK

### 결정 (1차)

**Prometheus(메트릭) + Logback JSON stdout** 2가지로 한정. 시각화·로그 집계는 후속.

### 근거

- Spring Boot Actuator + Micrometer 가 기본 통합 → 추가 라이브러리 불필요
- Prometheus 는 표준 메트릭 포맷, Grafana/AlertManager 등 생태계 풍부
- 로그는 JSON stdout 으로 출력만 하고 컨테이너 로그 드라이버에 위임 (운영 단계에서 ELK/Loki 도입)
- 1차 인프라 부담 최소화

### 트레이드오프

- Prometheus 시계열 보존 한정(2주~1개월) → 장기 보관은 Thanos/Cortex 후속
- 로그 검색은 stdout 으로만 노출 → 운영자는 docker logs/journalctl 사용

### 대안

- **ELK Stack**: 로그 + 메트릭 + APM 통합, 인프라 큼 (후속)
- **Grafana Loki**: Prometheus 친화 로그 집계, 인프라 추가 (후속)
- **Datadog/NewRelic**: SaaS, 비용 (공공기관 정책상 제외)

### 1차 적용

Actuator + Prometheus 엔드포인트 + Logback JSON. nginx 보호.

### 후속

운영 단계: Loki + Promtail (Grafana stack), 또는 ELK.

---

## 7. Docker 베이스 이미지

### 결정 (1차)

- 백엔드: `eclipse-temurin:17-jdk-alpine` (build) → `eclipse-temurin:17-jre-alpine` (runtime)
- 프론트: `node:20-alpine` (build) → `nginx:1.27-alpine` (runtime)
- DB: `postgres:16-alpine`

### 근거

- **eclipse-temurin**: AdoptOpenJDK 후속 공식 OpenJDK 이미지, multi-arch(amd64/arm64), 안정성 검증, OpenSSF 통과
- **alpine 변형**: 이미지 크기 50% 이하로 축소 (200MB → 100MB 수준)
- **nginx alpine**: 정적 자산 서빙 최적, 가벼움

### 트레이드오프

- alpine 의 musl libc → 일부 native 라이브러리 호환 이슈 (jjwt 등은 검증됨)
- distroless 는 디버깅 어려움 (1차 제외)

### 대안

- `amazoncorretto:17`: AWS 공식, 글래셜 GC 안정성 (1차 제외, 다음 후보)
- `gcr.io/distroless/java17`: 보안 최강, 디버깅 어려움 (1차 제외)
- `openjdk:17-slim` (deprecated): 사용하지 않음

### 1차 적용

eclipse-temurin alpine + tini(zombie reaper) + curl(헬스체크용).

### 후속

CIS 벤치마크 적용 시 distroless 또는 chainguard images 평가.

---

## 8. 점검 모드 구현 — Filter vs HandlerInterceptor

### 결정 (1차)

**Servlet Filter** (`MaintenanceFilter`) — Spring Security Filter Chain 의 가장 앞 단(`OncePerRequestFilter`, order=Highest).

### 근거

- Filter 는 JWT 검증 이전에 실행 → 점검 중에도 인증 비용 절약
- HandlerInterceptor 는 DispatcherServlet 이후 → 정적 리소스/Actuator 도 차단되는 부작용
- Actuator(`/actuator/*`)는 점검 모드 우회해야 함 → Filter URL 패턴으로 명시 제외
- ADMIN_IP_WHITELIST 와 JWT role 검사를 Filter 에서 통합 처리

### 트레이드오프

- Filter 에서 DB 조회(maintenance 테이블)는 매 요청 부담 → Caffeine 캐시 60초 적용
- ROLE 검사는 Filter 가 직접 JWT 디코드(Spring Security 가 아직 동작하지 않으므로)

### 대안

- **HandlerInterceptor**: 정적 리소스 차단 부작용
- **AOP @Around on Controller**: Controller 메서드 진입 후라 늦음
- **Reverse Proxy(nginx) 차단**: 가장 빠르나 점검 메시지 동적 변경 어려움

### 1차 적용

`MaintenanceFilter implements Filter` + 캐시 + Actuator/health/login URL 제외 화이트리스트.

### 후속

분산 환경 시 Spring Cloud Gateway 의 Filter 로 이동 검토.

---

## 부록: 결정 요약 테이블

| # | 영역 | 결정 | 1차 적용 |
|---|------|------|----------|
| 1 | 감사로그 적재 | 비동기 + 재시도 + fallback | `@Async` + RetryTemplate |
| 2 | APPEND-ONLY | DB 트리거 + 앱 레이어 | `fn_audit_log_immutable` |
| 3 | 통계 집계 | 배치 + 사전 집계 테이블 | `@Scheduled` cron |
| 4 | 보관 | PG 파티션 + 콜드 이관 | RANGE 월별 + 매뉴얼 절차 |
| 5 | 코드 캐시 | Caffeine | Spring Cache TTL 1h |
| 6 | 관측성 | Prometheus + JSON 로그 | Actuator + Logback |
| 7 | Docker 베이스 | eclipse-temurin alpine | Multi-stage |
| 8 | 점검 모드 | Servlet Filter | OncePerRequestFilter |

---

## 참고 문서

- Spring Boot Actuator: https://docs.spring.io/spring-boot/docs/3.2.x/reference/html/actuator.html
- Micrometer + Prometheus: https://micrometer.io/docs/registry/prometheus
- PostgreSQL Partitioning: https://www.postgresql.org/docs/16/ddl-partitioning.html
- Caffeine: https://github.com/ben-manes/caffeine/wiki
- Logback Logstash Encoder: https://github.com/logfellow/logstash-logback-encoder
- eclipse-temurin: https://hub.docker.com/_/eclipse-temurin
- pg_partman: https://github.com/pgpartman/pg_partman

---

_문서 버전: v0.1_
_작성일: 2026-04-29_

---

## 10. RFP 통합 보강 결정 사항 (v0.2)

본 절은 SPEC-CMS-005 v0.2 RFP 통합(REQ-SYSTEM-007-D ~ 010-D) 도입 시 도출한 5개 결정 사항을 정리한다.

### 10.1 KPI 계산 방식 — 사전 집계(배치) vs 실시간 + 캐시

#### 결정 (1차)

**사전 집계(배치) + 60초~1시간 Caffeine 캐시**. `KpiRefreshScheduler`가 `kpi_definition.refresh_interval_min` 주기로 SELECT 템플릿을 실행해 `kpi_value`에 적재하고, 대시보드 API는 `kpi_value`를 읽는다.

#### 근거

- 핵심 KPI 8종 중 PV/UV/STAY는 access_log GROUP BY로 비용이 크다. 매 요청 실시간 계산 시 p95 < 3초(REQ-SYSTEM-010-D-1) 위반 위험.
- 사전 집계는 SPEC-CMS-005 §3.1 (1차 포함 범위)의 일별/월별 배치 기조와 일치.
- 60초 단위 짧은 KPI(오늘 실시간 KPI)는 `refresh_interval_min=1`로 사전 집계되며, 이는 분 단위로 충분.

#### 트레이드오프

- 사전 집계는 분 단위 신선도(stale)가 있으나 운영 의사결정 KPI 특성상 허용.
- 실시간 계산은 쿼리 비용·DB 부하가 사용자 트래픽과 곱해져 위험.

#### 대안

- 실시간 + 길게 잡힌 캐시(5분): 캐시 미스 시 쿼리 비용 폭발 위험.
- Materialized View REFRESH: PG MV는 REFRESH 동안 락 또는 CONCURRENTLY 비용 큼. 1차 제외.
- TimescaleDB continuous aggregate: 인프라 추가, 후속 검토.

#### 1차 적용

`KpiRefreshScheduler` + `kpi_value` UPSERT + Caffeine 60초 캐시(대시보드 API 응답 단위).

### 10.2 엑셀 스트리밍 다운로드 라이브러리 — Apache POI SXSSF vs SQL→CSV stream

#### 결정 (1차)

**xlsx는 Apache POI `SXSSFWorkbook`(window=100), csv는 JDBC ResultSet streaming(`fetchSize=1000`)** 의 듀얼 트랙. 운영자가 format 파라미터로 선택.

#### 근거

- `SXSSFWorkbook`은 메모리에 sliding window(기본 100행)만 유지하고 디스크 임시 파일에 flush하므로 50만 행에서도 heap 200MB 이내 유지(REQ-SYSTEM-007-D-4 기준).
- CSV는 단순성·범용성이 강하고, JDBC fetchSize + ResponseBody OutputStream으로 행 단위 스트림이 자연스럽다.
- 두 포맷 모두 `Transfer-Encoding: chunked`로 응답해 클라이언트가 다운로드 진행률을 즉시 받을 수 있다.

#### 트레이드오프

- POI SXSSF는 CellStyle 메모리 누적 위험 → 스타일 캐시 재사용 패턴 적용 필수.
- CSV는 다국어 BOM, 줄바꿈 이스케이프, Excel 호환(UTF-8 with BOM) 처리 코드가 필요.

#### 대안

- xlsx 전용 + JDBC cursor 직접 사용: 하위 호환 어려움.
- 외부 워커(Worker pool)로 비동기 생성 후 다운로드 URL 발급: 1차 과잉, 후속 검토.
- streaming-excel(JOOQ) 라이브러리: 검증 부족, 1차 제외.

#### 1차 적용

`KpiExportService` 안에서 format 분기 → xlsx는 POI SXSSF, csv는 ResultSet 스트림. 둘 다 `OutputStream`을 직접 받아 controller에서 `StreamingResponseBody`로 반환.

### 10.3 integration_log vs audit_log 분리 사유

#### 결정 (1차)

**integration_log(외부 연계 기술 로그)와 audit_log(비즈니스 도메인 감사로그)를 별도 테이블로 분리**.

#### 근거

- audit_log는 컴플라이언스 보존(5년) + APPEND-ONLY(트리거) + 비즈니스 행위(C/U/D/권한) 추적 목적. 보존 기간이 길고 행 수가 많지 않다.
- integration_log는 운영 디버깅·SLA 모니터링 목적이며 호출량이 많고 보존은 6개월(개인정보보호법). 인덱스 설계와 archive 정책이 다르다.
- 두 로그를 합치면 보존 정책 충돌 + 인덱스 비효율 + 트리거 확장 시 외부 호출까지 immutable이 되는 부작용.

#### 트레이드오프

- 운영자가 "이 비즈니스 이벤트와 관련된 외부 호출"을 추적하려면 두 테이블을 JOIN해야 함 → trace_id 일치 + view 제공.

#### 대안

- 단일 테이블 + log_category 구분: 인덱스/보존 정책 분리 어려움. 1차 제외.
- 외부 로그 시스템(ELK)으로만 처리: SFR-015 "DB 분리 보관" 명시 요건 위반.

#### 1차 적용

`integration_log`(월별 PARTITION, 6개월) + `audit_log`(월별 PARTITION, 6개월 핫 + 5년 콜드). 두 로그는 trace_id MDC로 연관 추적.

### 10.4 외부 데이터 수집 스케줄러 — Spring @Scheduled 단일 vs ShedLock 멀티

#### 결정 (1차)

**Spring `@Scheduled` 단일 인스턴스**(SPEC-CMS-005 §3.1 / ASSUM-D-01 단일 노드 가정). ShedLock 도입은 멀티노드 전환 시 후속 SPEC.

#### 근거

- 1차 운영은 단일 백엔드 노드(SPEC-CMS-001 §17 운영 가정).
- Spring `@Scheduled`는 별도 인프라 없이 cron 표현식 + Bean 메서드로 동작.
- 멀티노드 전환 시 ShedLock(PG 또는 Redis backend)으로 분산 락 적용 가능 → 미리 인터페이스만 분리(`SchedulerLock` 추상화)해 두면 마이그레이션 비용 작음.

#### 트레이드오프

- 단일 노드 장애 시 동기화 미실행 → Docker healthcheck + 운영자 수동 재실행 절차로 보완.
- ShedLock은 PG row lock 부담이 있으나 1분 단위 cron에서 무시 가능.

#### 대안

- Quartz cluster: 운영 복잡도 큼. 1차 제외.
- AWS EventBridge + Lambda: 클라우드 종속, 공공기관 정책 검토 필요.

#### 1차 적용

`@Scheduled(cron=...)` Bean + `ExternalDataSyncService`. 멀티노드 시 ShedLock 4.x로 전환.

### 10.5 RFP 성능 임계값 검증 방법 — JMeter + Prometheus 룰

#### 결정 (1차)

**JMeter 부하 테스트 시나리오 + Prometheus 알람 룰** 듀얼 검증.

- JMeter: 50 RPS 10분, 1,000 동시 사용자 시나리오 (`tests/load/{api_search.jmx, api_dashboard.jmx, peak_users.jmx}`)
- Prometheus 룰 5종(`ApiLatencyHigh`, `BatchSlaBreach`, `NodeCpuHigh`, `NodeMemHigh`, `NodeDiskHigh`)을 `deploy/prometheus/rules/`에 배치.

#### 근거

- JMeter는 일회성·검수용 부하 측정에 적합하고 GUI/CLI 모두 지원.
- Prometheus 룰은 운영 중 지속 모니터링 + 알림. 두 채널 결합으로 출시 직전 검증과 운영 중 회귀 모두 커버.
- Spring Boot Actuator + Micrometer는 이미 SPEC-CMS-005 v0.1 §10에 도입되어 추가 인프라 없음.

#### 트레이드오프

- JMeter는 Java 기반이라 CI 환경에서 무겁다 → 별도 Docker 이미지로 격리.
- Prometheus 룰의 thresholds(예: p95 3초, 5분 연속)는 운영 데이터 누적 후 조정 필요.

#### 대안

- k6 / Gatling: 가볍고 코드 시나리오. JMeter 대체 가능하나 공공기관 사례 적음. 후속 검토.
- 자체 부하 스크립트: 검증 신뢰 낮음. 제외.

#### 1차 적용

JMeter `.jmx` + Prometheus rule yaml + Grafana 대시보드(운영 단계). RUN phase에서 룰 yaml 작성 + JMeter 시나리오 작성을 함께 산출물로 포함.

---

_문서 버전: v0.2_
_갱신일: 2026-04-29 (RFP 통합 보강: REQ-SYSTEM-007-D ~ 010-D 결정 사항 5종 추가)_
