package kr.co.ircp.cms.domain.governance.repository;

import kr.co.ircp.cms.domain.governance.entity.BoardStatsDaily;
import kr.co.ircp.cms.domain.governance.entity.BoardStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.ContentViewStatsDaily;
import kr.co.ircp.cms.domain.governance.entity.ContentViewStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.PolicyMatchStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.SafetyStatsMonthly;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 거버넌스 통계 집계 매퍼.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001~004 — board/content/policy/safety stats 집계 SQL.
 */
@Mapper
public interface GovernanceStatsMapper {

    /** REQ-DATA-001: access_log + bbs_post + bbs_comment → board_stats_daily UPSERT. 집계된 board 행 수 반환. */
    int upsertBoardStatsDaily(@Param("targetDate") LocalDate targetDate);

    /** board_stats_daily → board_stats_monthly 월 합산 UPSERT. 집계 행 수 반환. */
    int upsertBoardStatsMonthly(@Param("targetMonth") String targetMonth);

    /** REQ-DATA-002: access_log /contents/{id} → content_view_stats_daily UPSERT. */
    int upsertContentViewStatsDaily(@Param("targetDate") LocalDate targetDate);

    /** content_view_stats_daily → monthly UPSERT. */
    int upsertContentViewStatsMonthly(@Param("targetMonth") String targetMonth);

    /** REQ-DATA-003: SPEC-CMS-007 데이터 → policy_match_stats_monthly UPSERT. 미존재 시 0 반환. */
    int upsertPolicyMatchStatsMonthly(@Param("targetMonth") String targetMonth);

    /** REQ-DATA-004: SPEC-CMS-006 데이터 → safety_stats_monthly UPSERT. 미존재 시 0 반환. */
    int upsertSafetyStatsMonthly(@Param("targetMonth") String targetMonth);

    /** 의존 SPEC 테이블 존재 여부 (information_schema). */
    int countTable(@Param("tableName") String tableName);

    /** 검증용 — 특정 일자의 board_stats_daily 행 수. */
    int countBoardStatsDaily(@Param("statDate") LocalDate statDate);

    List<BoardStatsDaily> findBoardStatsDaily(@Param("statDate") LocalDate statDate);

    List<ContentViewStatsDaily> findContentViewStatsDaily(@Param("statDate") LocalDate statDate);

    // ─── REST 조회용 (Step 2) ────────────────────────────────────────────────

    /** params: boardId?, from(LocalDate), to(LocalDate). */
    List<BoardStatsDaily> findBoardStatsDailyRange(@Param("p") Map<String, Object> params);

    /** params: boardId?, from(YYYY-MM), to(YYYY-MM). */
    List<BoardStatsMonthly> findBoardStatsMonthlyRange(@Param("p") Map<String, Object> params);

    /** params: contentId?, from(LocalDate), to(LocalDate). */
    List<ContentViewStatsDaily> findContentViewStatsDailyRange(@Param("p") Map<String, Object> params);

    /** params: contentId?, from(YYYY-MM), to(YYYY-MM). */
    List<ContentViewStatsMonthly> findContentViewStatsMonthlyRange(@Param("p") Map<String, Object> params);

    /** params: policyId?, from(YYYY-MM), to(YYYY-MM). */
    List<PolicyMatchStatsMonthly> findPolicyMatchStatsRange(@Param("p") Map<String, Object> params);

    /** params: category?, from(YYYY-MM), to(YYYY-MM). */
    List<SafetyStatsMonthly> findSafetyStatsRange(@Param("p") Map<String, Object> params);
}
