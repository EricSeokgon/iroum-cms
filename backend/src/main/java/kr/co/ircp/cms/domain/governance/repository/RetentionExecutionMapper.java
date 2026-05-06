package kr.co.ircp.cms.domain.governance.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 보존 정책 실행용 SQL 매퍼.
 *
 * <p>각 RetentionJob에서 INSERT-SELECT (archive) + DELETE 패턴 실행.
 * SPEC-CMS-009 REQ-GOV-007~009.
 */
@Mapper
public interface RetentionExecutionMapper {

    /**
     * personal_data_access_log 6개월 경과 → archive 이관.
     * 반환: archive INSERT 행 수.
     */
    int archivePersonalDataAccessLog(@Param("retentionMonths") int retentionMonths);

    /** archive 후 source DELETE. APPEND-ONLY 트리거 우회는 운영 단계 절차. */
    int deletePersonalDataAccessLog(@Param("retentionMonths") int retentionMonths);

    /** login_history 12개월 경과 DELETE. */
    int deleteLoginHistory(@Param("retentionMonths") int retentionMonths);

    /** access_log 3개월 경과 DELETE (PARTITION 단위 DROP은 후속 SPEC). */
    int deleteAccessLog(@Param("retentionMonths") int retentionMonths);

    /** integration_log 존재 여부. */
    int integrationLogExists();

    /** integration_log 6개월 경과 archive 이관. integration_log 미존재 시 0. */
    int archiveIntegrationLog(@Param("retentionMonths") int retentionMonths);

    int deleteIntegrationLog(@Param("retentionMonths") int retentionMonths);

    /** audit_log 6개월 경과 archive INSERT-SELECT. archive 테이블 자동 생성 보장. */
    int ensureAuditLogArchive();

    int archiveAuditLog(@Param("retentionMonths") int retentionMonths);

    int deleteAuditLogArchived(@Param("retentionMonths") int retentionMonths);
}
