package kr.co.ircp.cms.domain.dashboard.preference.repository;

import kr.co.ircp.cms.domain.dashboard.preference.entity.UserDashboardPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 — user_dashboard_preference MyBatis 매퍼.
 *
 * <p>1:1 관계 (PK = user_id). lazy 생성 패턴:
 * <ol>
 *   <li>{@link #findByUserId(Long)} 결과가 비어 있으면 {@link #upsertDefaults(Long)} 호출</li>
 *   <li>이후 다시 {@link #findByUserId(Long)} 로 row 확보</li>
 * </ol>
 */
@Mapper
public interface UserDashboardPreferenceMapper {

    Optional<UserDashboardPreference> findByUserId(@Param("userId") Long userId);

    /**
     * 모든 컬럼이 DEFAULT 값을 갖는 row 를 idempotent 하게 생성.
     * 이미 존재하면 변경 없음 (ON CONFLICT DO NOTHING).
     */
    int upsertDefaults(@Param("userId") Long userId);

    /**
     * 부분 갱신. NULL 인 컬럼은 변경하지 않는다 (COALESCE 패턴).
     *
     * <p>{@code expectedUpdatedAt} 가 null 이면 낙관적 잠금 검사 생략, 아니면 일치하는 행만 갱신.
     * 결과 0 행이면 호출자가 {@code PreferenceConflictException} 으로 변환한다.
     */
    int patch(
            @Param("userId") Long userId,
            @Param("theme") String theme,
            @Param("density") String density,
            @Param("fontScale") BigDecimal fontScale,
            @Param("colorPalettePreference") String colorPalettePreference,
            @Param("sidebarCollapsed") Boolean sidebarCollapsed,
            @Param("refreshIntervalSeconds") Integer refreshIntervalSeconds,
            @Param("hasRefreshIntervalSeconds") Boolean hasRefreshIntervalSeconds,
            @Param("expectedUpdatedAt") Instant expectedUpdatedAt
    );

    /**
     * REQ-DP-001-1 / 001-2 / 001-5: 위젯 가시성 JSON 전체 교체.
     *
     * <p>호출자는 ObjectMapper 로 신규 JSON 문자열을 생성해 전달.
     */
    int updateHiddenWidgetInstanceIds(
            @Param("userId") Long userId,
            @Param("hiddenJson") String hiddenJson
    );

    /**
     * REQ-DP-002-5: 스타일 5종을 DEFAULT 로 초기화 (hidden 은 보존).
     */
    int resetStyleToDefault(@Param("userId") Long userId);

    /**
     * 레이아웃 삭제 시 모든 사용자 preference 에서 해당 layoutId 키를 제거.
     *
     * <p>DashboardLayoutServiceImpl.delete() 에서 트랜잭션 내 호출.
     */
    int cleanupForLayout(@Param("layoutId") Long layoutId);
}
