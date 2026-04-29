package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.VerificationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * 본인인증 이력 MyBatis Mapper.
 *
 * <p>REQ-AUTH-017-D-5 — verification_history 테이블 접근. IP 기반 차단 판단에 사용.
 */
@Mapper
public interface VerificationHistoryMapper {

    /**
     * 이력 삽입.
     */
    void insert(VerificationHistory entry);

    /**
     * 지정 시각 이후 동일 IP에서 발생한 실패 건수 집계.
     *
     * <p>IP 차단 기준: 시간당 10회 초과.
     */
    int countRecentByIp(@Param("ipHash") String ipHash, @Param("from") Instant from);
}
