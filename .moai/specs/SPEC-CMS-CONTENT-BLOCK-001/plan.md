# SPEC-CMS-CONTENT-BLOCK-001 구현 계획

## 기술 스택

- **Backend**: Spring Boot 3.3 / Java 17 / MyBatis / PostgreSQL
- **Frontend**: Vue 3 + TypeScript + Element Plus
- **테스트**: JUnit 5 + MockMvc + Docker (`eclipse-temurin:17-jdk-jammy`)
- **메서드**: TDD (RED-GREEN-REFACTOR)

## 참조 구현체

| 참조 | 파일 | 참고 포인트 |
|------|------|-------------|
| Banner 엔티티 패턴 | `backend/src/main/java/kr/co/ircp/cms/domain/content/banner/entity/Banner.java` | Lombok 어노테이션, Instant 타입, String 상태 |
| Banner 서비스 패턴 | `.../banner/service/BannerServiceImpl.java` | @Transactional(readOnly), AuditLogService 주입, 예외 처리 |
| Banner 매퍼 XML | `backend/src/main/resources/mapper/content/BannerMapper.xml` | resultMap, useGeneratedKeys, 동적 쿼리 |
| Banner 컨트롤러 | `.../banner/controller/BannerController.java` | @PreAuthorize, ResponseEntity, @AuthenticationPrincipal |
| Popup 컨트롤러 | `.../popup/controller/PopupController.java` | PATCH 상태 토글 패턴 |
| 프론트엔드 뷰 | `frontend/admin/src/views/content/BannerManagerView.vue` | Composition API, el-dialog, el-form 패턴 |
| 프론트엔드 API | `frontend/admin/src/api/content.ts` | interface 정의, API 함수 구조 |

## 태스크 분해

### Task 1: DB 마이그레이션
- **파일**: `backend/src/main/resources/db/migration/V45__shared_content_block.sql`
- **내용**: shared_content_block 테이블, CHECK 제약, 인덱스
- **TDD**: 마이그레이션 실행 후 테이블 존재 및 제약 검증

### Task 2: 엔티티 + 매퍼
- **파일**:
  - `entity/SharedContentBlock.java` — Lombok @Data @Builder, Instant 타입
  - `mapper/SharedContentBlockMapper.java` — @Mapper 인터페이스
  - `mapper/content/SharedContentBlockMapper.xml` — resultMap, CRUD SQL
- **TDD**: 매퍼 단위 테스트 (insert → findById 검증)

### Task 3: DTO
- **파일**:
  - `dto/ContentBlockRequest.java` — record 타입, @NotBlank, @NotNull 검증
  - `dto/ContentBlockResponse.java` — record 타입, static from(SharedContentBlock) 팩토리
- **TDD**: DTO 변환 단위 테스트

### Task 4: 예외 클래스
- **파일**:
  - `exception/ContentBlockNotFoundException.java`
  - `exception/ContentBlockSlugDuplicateException.java`
  - `exception/ContentBlockTypeInvalidException.java`

### Task 5: 서비스
- **파일**:
  - `service/SharedContentBlockService.java` — 인터페이스
  - `service/SharedContentBlockServiceImpl.java` — 구현체 (AuditLogService 주입, Jsoup 정제)
- **주요 메서드**:
  - `create(ContentBlockRequest, Long createdBy)` → ContentBlockResponse
  - `findAll(String status, String type)` → List<ContentBlockResponse>
  - `findById(Long id)` → ContentBlockResponse
  - `update(Long id, ContentBlockRequest)` → ContentBlockResponse
  - `delete(Long id)` → void
  - `toggleStatus(Long id, String status)` → ContentBlockResponse
- **TDD**: 서비스 단위 테스트 (Mockito 매퍼 mock)

### Task 6: 컨트롤러
- **파일**: `controller/ContentBlockController.java`
- **엔드포인트**:
  - `GET /api/v1/content/blocks` — `@PreAuthorize("hasAuthority('CONTENT:READ')")`
  - `POST /api/v1/content/blocks` — `@PreAuthorize("hasAuthority('CONTENT:WRITE')")`
  - `GET /api/v1/content/blocks/{id}` — `@PreAuthorize("hasAuthority('CONTENT:READ')")`
  - `PUT /api/v1/content/blocks/{id}` — `@PreAuthorize("hasAuthority('CONTENT:WRITE')")`
  - `DELETE /api/v1/content/blocks/{id}` — `@PreAuthorize("hasAuthority('CONTENT:WRITE')")`
  - `PATCH /api/v1/content/blocks/{id}/status` — `@PreAuthorize("hasAuthority('CONTENT:WRITE')")`
- **HTML 타입 검사**: HTML 타입 블록은 서비스 레이어에서 `principal.roles()` 확인

### Task 7: 통합 테스트
- **파일**: `backend/src/test/java/kr/co/ircp/cms/domain/content/block/ContentBlockIT.java`
- **테스트 환경**: Docker (`eclipse-temurin:17-jdk-jammy`), AbstractIntegrationTest 상속
- **테스트 케이스**: AC-001 ~ AC-011 커버

### Task 8: 프론트엔드 API 모듈
- **파일**: `frontend/admin/src/api/content.ts`
- **추가 내용**:
  - `SharedContentBlockRequest` interface
  - `SharedContentBlockResponse` interface
  - `contentBlocks` API 함수 객체 (list, create, get, update, delete, toggleStatus)

### Task 9: 프론트엔드 뷰
- **파일**: `frontend/admin/src/views/content/ContentBlockManagerView.vue`
- **구성**: el-table 목록 + el-dialog 생성·수정 폼 + 타입별 에디터 전환
- **상태**: 블록 타입(RICH_TEXT/MARKDOWN/HTML/EMBED)별 입력 필드 동적 전환

### Task 10: 라우터 등록
- **파일**: `frontend/admin/src/router/index.ts`
- **추가**: `{ path: 'content/blocks', name: 'content-blocks', component: () => import('@/views/content/ContentBlockManagerView.vue') }`

## MX 태그 계획

```yaml
mx_plan:
  anchor_targets:
    - SharedContentBlockService.create() — 공개 API 경계, 컨트롤러에서 호출
    - SharedContentBlockService.update() — HTML 정제 불변 계약
  warn_targets:
    - ContentBlockController (HTML 타입 조건부 권한 분기)
  note_targets:
    - SharedContentBlock.slug — 형식 제약 (소문자 알파벳·숫자·하이픈)
    - SharedContentBlockServiceImpl (Jsoup 정제 안전 계약)
```

## 위험 요소 및 완화

| 위험 | 완화 방안 |
|------|-----------|
| HTML 타입 XSS | SUPER_ADMIN 전용 제한 + Jsoup 화이트리스트 재검토 |
| slug 중복 race condition | DB UNIQUE 제약으로 최종 방어, 서비스에서 사전 검증 |
| audit_log 제약 위반 | action 값을 CREATE/UPDATE/DELETE 만 사용 (hardcoded) |
| 빌드 환경 Java 8 | 테스트 전체를 Docker 컨테이너 내부에서 실행 |
