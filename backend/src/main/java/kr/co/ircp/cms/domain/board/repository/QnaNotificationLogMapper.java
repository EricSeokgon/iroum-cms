package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.QnaNotificationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Q&A 답변 알림 발송 로그 MyBatis 매퍼.
 * REQ-BOARD-014-D-3: 멱등성·재시도·DEAD_LETTER 상태 추적
 */
@Mapper
public interface QnaNotificationLogMapper {

    /** 최초 INSERT (PENDING 상태). */
    void insert(QnaNotificationLog log);

    /** 발송 성공 처리. */
    void markSent(@Param("id") Long id);

    /** 재시도 실패 기록. */
    void markFailed(@Param("id") Long id, @Param("error") String error);

    /** DEAD_LETTER 전환 (3회 실패 후). */
    void markDeadLetter(@Param("id") Long id, @Param("error") String error);

    /** 재시도 대상 조회 (PENDING 또는 FAILED, retry_count < 3). */
    List<QnaNotificationLog> findPendingOrFailed();
}
