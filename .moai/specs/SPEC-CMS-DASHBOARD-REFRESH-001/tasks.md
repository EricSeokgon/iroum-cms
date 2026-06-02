## Task Decomposition
SPEC: SPEC-CMS-DASHBOARD-REFRESH-001

| Task ID | Description | Requirement | Dependencies | Planned Files | Status |
|---------|-------------|-------------|--------------|---------------|--------|
| T-001 | V42 DB 마이그레이션 | REQ-REFRESH-001-1,2 | - | V42__user_dashboard_preference_refresh_interval.sql | pending |
| T-002 | 백엔드 Entity 확장 | REQ-REFRESH-001-1 | T-001 | UserDashboardPreference.java | pending |
| T-003 | 백엔드 DTO 확장 (Response+Request) | REQ-REFRESH-001-1,2 | T-002 | PreferenceResponse.java, PreferenceUpdateRequest.java | pending |
| T-004 | 백엔드 Mapper 확장 | REQ-REFRESH-001-1,2 | T-003 | UserDashboardPreferenceMapper.java, UserDashboardPreferenceMapper.xml | pending |
| T-005 | 백엔드 Service 확장 (화이트리스트 검증) | REQ-REFRESH-001-1,2 | T-004 | UserDashboardPreferenceServiceImpl.java | pending |
| T-006 | 백엔드 단위/통합 테스트 | REQ-REFRESH-001-1,2 | T-005 | UserDashboardPreferenceServiceTest.java, UserDashboardPreferenceIT.java | pending |
| T-007 | FE API 타입 확장 | REQ-REFRESH-001-1 | T-006 | api/dashboardPreference.ts | pending |
| T-008 | FE Store 확장 | REQ-REFRESH-001-3 | T-007 | stores/dashboardPreferenceStore.ts | pending |
| T-009 | FE useDashboardAutoRefresh 컴포저블 | REQ-REFRESH-002-1,2,3,REQ-REFRESH-003,004 | T-008 | composables/useDashboardAutoRefresh.ts | pending |
| T-010 | FE DashboardRefreshIndicator 컴포넌트 | REQ-REFRESH-003-1,2 | T-009 | components/dashboard/DashboardRefreshIndicator.vue | pending |
| T-011 | FE DashboardPreferencePanel 주기 선택 UI | REQ-REFRESH-001-1 | T-010 | views/dashboard/DashboardPreferencePanel.vue, i18n 키 | pending |
