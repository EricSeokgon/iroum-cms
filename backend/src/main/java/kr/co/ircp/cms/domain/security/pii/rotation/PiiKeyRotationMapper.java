package kr.co.ircp.cms.domain.security.pii.rotation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * PII 키 회전 배치 전용 MyBatis Mapper.
 *
 * <p>SPEC-CMS-SECURITY-PII-ROTATION-001 REQ-PII-ROT-001/002/003.
 *
 * <p>책임:
 * <ol>
 *   <li>활성 키 버전과 다른 row 페이징 조회 ({@link #findUsersWithOldKeyVersion})</li>
 *   <li>재암호화된 4개 컬럼 UPDATE — HMAC 컬럼은 건드리지 않음 ({@link #updateUserEmailPii})</li>
 *   <li>회전 실행 로그 INSERT / 종료 처리 ({@link #insertRotationLog} / {@link #completeRotationLog})</li>
 * </ol>
 *
 * <p>SQL 정의: {@code mybatis/mapper/security/PiiKeyRotationMapper.xml}.
 */
@Mapper
public interface PiiKeyRotationMapper {

    /**
     * 활성 키 버전과 다른 email_key_version 을 가진 사용자 row 를 lastId 기준 페이징 조회한다.
     *
     * <p>id 기반 cursor 페이징을 사용한다 (WHERE 조건이 회전 진행에 따라 변하지만,
     * id 정렬은 안정적). 호출 측은 마지막 조회한 id 를 lastId 로 넘겨 다음 청크를 가져온다.
     *
     * @param activeVersion 현재 활성 키 버전 (이 값과 다른 row 만 반환)
     * @param limit 최대 반환 row 수 (= batchSize)
     * @param lastId 이전 청크 마지막 id (첫 호출 시 0)
     * @return 재암호화 대상 row 목록 (id ASC)
     */
    List<UserPiiRow> findUsersWithOldKeyVersion(
            @Param("activeVersion") int activeVersion,
            @Param("limit") int limit,
            @Param("lastId") long lastId);

    /**
     * 단일 사용자의 PII 4개 컬럼을 새 키 버전으로 UPDATE 한다.
     *
     * <p>email_hmac 컬럼은 갱신하지 않는다 — HMAC 키는 DEK 회전 대상이 아니다.
     *
     * @return 업데이트된 row 수 (정상 시 1)
     */
    int updateUserEmailPii(
            @Param("userId") long userId,
            @Param("emailEncrypted") byte[] emailEncrypted,
            @Param("emailIv") byte[] emailIv,
            @Param("emailTag") byte[] emailTag,
            @Param("emailKeyVersion") int emailKeyVersion);

    /**
     * 회전 시작 로그를 INSERT 하고 생성된 id 를 반환한다.
     *
     * <p>MyBatis 의 insert statement 반환값은 affected row count 이므로,
     * 생성 PK 를 받아오기 위해 {@link RotationLogInsert} holder 에 keyProperty 로 채워준다.
     *
     * @return pii_key_rotation_log.id (auto-generated)
     */
    default long insertRotationLog(int oldVersion, int newVersion) {
        RotationLogInsert holder = new RotationLogInsert(oldVersion, newVersion);
        doInsertRotationLog(holder);
        Long id = holder.getId();
        if (id == null) {
            throw new IllegalStateException("pii_key_rotation_log id 생성 실패");
        }
        return id;
    }

    /**
     * 내부 전용 INSERT — keyProperty="id" 로 생성 PK 를 holder 에 채워준다.
     * 외부 호출자는 {@link #insertRotationLog(int, int)} 를 사용한다.
     */
    void doInsertRotationLog(RotationLogInsert holder);

    /**
     * 회전 종료 처리 — finished_at, migrated_rows, status, error_message UPDATE.
     *
     * @param logId insertRotationLog 가 반환한 id
     * @param migratedRows 재암호화 완료 row 수
     * @param status COMPLETED 또는 FAILED
     * @param errorMessage 실패 시 메시지 (성공 시 null)
     */
    void completeRotationLog(
            @Param("logId") long logId,
            @Param("migratedRows") int migratedRows,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage);
}
