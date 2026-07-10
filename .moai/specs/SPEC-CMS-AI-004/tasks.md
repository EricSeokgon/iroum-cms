# Task Decomposition
SPEC: SPEC-CMS-AI-004

| Task ID | Description | Requirement | Dependencies | Planned Files | Status |
|---------|-------------|-------------|--------------|---------------|--------|
| T-001 | ML DTO 신규 (Java record) — TagRecommendationRequest/Response | REQ-AI-TAG-001 | - | infra/ml/dto/TagRecommendationRequest.java, infra/ml/dto/TagRecommendationResponse.java | done |
| T-002 | MlServiceClient 인터페이스 + Mock 구현 | REQ-AI-TAG-001/004 | T-001 | infra/ml/MlServiceClient.java, test/.../infra/ml/MockMlServiceClient.java, test/.../infra/ml/MlServiceClientTest.java | done |
| T-003 | V54 DB 마이그레이션 + Testcontainers IT | REQ-AI-TAG-011~014 | T-001 | db/migration/V54__ai_tag_recommendation.sql, test/.../domain/ai/tag/TagRecommendationLogMigrationIT.java | done |
| T-004 | MlServiceClientImpl.tagRecommendation 구현 (RestTemplate+CB+폴백) | REQ-AI-TAG-002/003 | T-002 | infra/ml/MlServiceClientImpl.java, application.yml | done |
| T-005 | 캐시 빈 + 비동기 로그 서비스/mapper/XML | REQ-AI-TAG-010~013 | T-003 | config/CacheConfig.java, domain/ai/tag/service/AiTagRecommendationLogService.java, domain/ai/tag/model/AiTagRecommendationLog.java, domain/ai/tag/mapper/AiTagRecommendationLogMapper.java, resources/mapper/ai/tag/AiTagRecommendationLogMapper.xml | done |
| T-006 | 도메인 서비스 (20자 가드/캐시/폴백/로깅) | REQ-AI-TAG-006/008~010/013 | T-004/T-005 | domain/ai/tag/service/TagRecommendationService.java, domain/ai/tag/dto/*.java | done |
| T-007 | REST 컨트롤러 + IT (관리자 인증+시민 비인증) | REQ-AI-TAG-006/007/012 | T-006 | domain/ai/tag/controller/TagRecommendationController.java, test/.../domain/ai/tag/TagRecommendationControllerIT.java | done |
| T-008 | SecurityConfig 화이트리스트 등록 | REQ-AI-TAG-007, NFR-003 | T-007 | config/SecurityConfig.java | done |
| T-009 | OpenAPI 계약 확장 (POST /ml/v1/tag-recommend) | REQ-AI-TAG-005 | T-001 | docs/ai-ml-service-openapi.yaml | done |
| T-010 | useTagRecommendation.ts 컴포저블 (admin) | NFR-001 | T-007 | admin/src/composables/useTagRecommendation.ts | done |
| T-011 | TagRecommendationInput.vue 재사용 컴포넌트 | REQ-AI-TAG-012 | T-010 | admin/src/components/TagRecommendationInput.vue | done |
| T-012 | PostFormView.vue 통합 + tags 저장 payload | REQ-AI-TAG-015 | T-011 | admin/src/views/board/PostFormView.vue | done |
| T-013 | 게시글 목록/상세 읽기 전용 태그 칩 | REQ-AI-TAG-015 | T-012 | admin/src/views/board/PostListView.vue 외 | done |
| T-014 | QnaCreateView.vue 통합 (public) | REQ-AI-TAG-007, NFR-003 | T-011 | public/src/views/qnas/QnaCreateView.vue, public/src/composables/useTagRecommendation.ts | done |
| T-016 | [버그픽스] Post/QNA Java DTO + MyBatis mapper에 tags 필드 추가 | REQ-AI-TAG-015 | T-003 | backend/.../domain/board/dto/{Post*Request,PostDetail}.java, mapper/board/PostMapper.xml 외 | done |
| T-015 | [옵션/stretch] 태그 채택률 메트릭 뷰 | - | T-014 | - | pending |
