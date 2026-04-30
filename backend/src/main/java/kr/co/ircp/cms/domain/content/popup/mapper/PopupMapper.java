package kr.co.ircp.cms.domain.content.popup.mapper;

import kr.co.ircp.cms.domain.content.popup.entity.Popup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 팝업 MyBatis 매퍼.
 * REQ-CONTENT-008-D: 팝업 CRUD + 활성 팝업 조회
 *
 * // @MX:ANCHOR: [AUTO] PopupMapper — 팝업 데이터 접근 계층
 * // @MX:REASON: PopupService, PopupController에서 fan_in >= 3으로 참조
 */
@Mapper
public interface PopupMapper {

    Optional<Popup> findById(@Param("id") Long id);

    /** 사이트별 전체 팝업 목록 */
    List<Popup> findBySiteId(@Param("siteId") Long siteId);

    /**
     * 활성 팝업 조회 (status=ACTIVE AND show_from <= now <= show_until).
     * REQ-CONTENT-008-D-2: 노출 시간 윈도우 필터
     */
    List<Popup> findActiveByTimeWindow(
            @Param("siteId") Long siteId,
            @Param("now") Instant now
    );

    void insert(Popup popup);

    int update(Popup popup);

    int deleteById(@Param("id") Long id);
}
