## SPEC-CMS-AI-004 Progress

- Started: 2026-06-15
- Harness: standard
- Language skills: moai-lang-java, moai-lang-typescript
- Scale mode: Full Pipeline (domains=3, files=15+)
- Development mode: TDD (RED-GREEN-REFACTOR)

## Phase 1 완료 (2026-06-15)

- T-001~T-009 백엔드 구현 완료
- Unit 테스트: 2072 pass / 0 fail
- IT 테스트: TagRecommendationLogMigrationIT 5/5, TagRecommendationControllerIT 6/6
- @MX: ANCHOR(MlServiceClient), NOTE(TagRecommendationService), WARN(SecurityConfig)
- V54 마이그레이션: ai_tag_recommendation_log + bbs_post/qna tags 컬럼
- 기존 MlServiceClient 7개 메서드 불변 확인

## Phase 2+3 완료 (2026-06-15)

- T-010~T-014 프론트엔드 구현 완료
- Admin: useTagRecommendation.ts (debounce 500ms), TagRecommendationInput.vue, PostFormView/ListView/DetailView 통합
- Public: QnaCreateView.vue 통합, public composable/api
- type-check: 어드민/공공 0 errors
- 테스트: admin 414 tests pass, public 249 tests pass
- T-016 버그픽스: Post/QNA DTO + MyBatis mapper에 tags LIST<String> 배선
  - 기존 StringArrayTypeHandler 재사용 (media_asset.tags 선례)
  - 관련 46건 단위 테스트 전부 통과

## 구현 완료 항목 (2026-06-15)
T-001~T-014, T-016 — 총 15개 태스크 완료 (T-015 stretch 제외)
