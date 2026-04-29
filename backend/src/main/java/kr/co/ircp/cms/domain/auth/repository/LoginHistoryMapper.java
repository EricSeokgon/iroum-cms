package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.LoginHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 로그인 이력 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 성공/실패 로그인 이력 기록 및 조회.
 * SQL은 mybatis/mapper/auth/LoginHistoryMapper.xml에 정의.
 */
@Mapper
public interface LoginHistoryMapper {

    /**
     * 로그인 이력 삽입.
     *
     * <p>성공/실패 무관하게 모든 로그인 시도를 기록한다.
     */
    void insert(LoginHistory history);

    /**
     * 특정 사용자의 최근 이력 조회.
     *
     * <p>관리자 조회 또는 연속 실패 분석에 활용.
     */
    List<LoginHistory> findRecentByUsername(
            @Param("username") String username,
            @Param("limit") int limit);
}
