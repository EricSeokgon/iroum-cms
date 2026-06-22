## Task Decomposition
SPEC: SPEC-CMS-PAGE-HISTORY-001

| Task ID | Description | Requirement | Dependencies | Planned Files | Status |
|---------|-------------|-------------|--------------|---------------|--------|
| T-001 | Fix PageServiceImpl.rollbackPage() to parse snapshot JSON and restore fields; complete PageIT AC-PAGE-10 with real seeding | REQ-PHIST-002 | - | backend/src/main/java/kr/co/ircp/cms/domain/content/page/service/PageServiceImpl.java, backend/src/test/java/kr/co/ircp/cms/domain/content/PageIT.java | completed |
| T-002 | Create PageChangeSummaryGenerator (pure util); wire into PageServiceImpl.updatePage() when changeSummary blank | REQ-PHIST-003 | T-001 (touches same service) | backend/src/main/java/kr/co/ircp/cms/domain/content/page/service/PageChangeSummaryGenerator.java (NEW), backend/src/test/java/kr/co/ircp/cms/domain/content/page/service/PageChangeSummaryGeneratorTest.java (NEW), backend/src/main/java/kr/co/ircp/cms/domain/content/page/service/PageServiceImpl.java | completed |
| T-003 | Add @AuditLog(action=UPDATE) to rollbackPage() with captureReturn=true | REQ-PHIST-004 | T-001 (rollback must work first) | backend/src/main/java/kr/co/ircp/cms/domain/content/page/service/PageServiceImpl.java | completed |
| T-004 | Create PageHistoryRetentionJob batch; add findPageIdsWithExcessHistory + deleteOldestExceedingLimit to mapper; configure max-versions property | REQ-PHIST-001 | - (independent) | backend/src/main/java/kr/co/ircp/cms/domain/content/page/service/PageHistoryRetentionJob.java (NEW), backend/src/main/resources/mapper/content/PageHistoryMapper.xml, backend/src/main/resources/application.yml | completed |
| T-005 | Add history button to PageListView action column; import PageHistoryDialog; handle rolledBack event | REQ-PHIST-005 | - (independent) | frontend/admin/src/views/content/PageListView.vue | completed |
