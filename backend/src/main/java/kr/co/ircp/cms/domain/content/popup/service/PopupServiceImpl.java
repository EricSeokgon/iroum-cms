package kr.co.ircp.cms.domain.content.popup.service;

import kr.co.ircp.cms.domain.content.popup.dto.PopupActiveResponse;
import kr.co.ircp.cms.domain.content.popup.dto.PopupRequest;
import kr.co.ircp.cms.domain.content.popup.dto.PopupResponse;
import kr.co.ircp.cms.domain.content.popup.entity.Popup;
import kr.co.ircp.cms.domain.content.popup.exception.PopupPeriodInvalidException;
import kr.co.ircp.cms.domain.content.popup.exception.PopupTargetMissingException;
import kr.co.ircp.cms.domain.content.popup.mapper.PopupMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 팝업 서비스 구현체.
 * REQ-CONTENT-008-D: 팝업 CRUD + 활성 팝업 조회 + 캐시
 *
 * // @MX:ANCHOR: [AUTO] PopupServiceImpl — 팝업 전체 라이프사이클 관리
 * // @MX:REASON: PopupController, CacheEvict(popupActive)에서 fan_in >= 3으로 참조
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopupServiceImpl implements PopupService {

    /** 활성 팝업 최대 반환 수 (X-Popup-Limit 헤더 값과 동일) */
    private static final int POPUP_LIMIT = 5;

    private final PopupMapper popupMapper;

    /**
     * 팝업 등록.
     * REQ-CONTENT-008-D-1: show_from < show_until 검증, ROLE 타겟 시 역할 코드 필수, HTML sanitize
     */
    @Override
    @Transactional
    @CacheEvict(value = "popupActive", allEntries = true)
    public PopupResponse registerPopup(PopupRequest request) {
        // 기간 검증
        if (!request.showFrom().isBefore(request.showUntil())) {
            throw new PopupPeriodInvalidException();
        }

        // ROLE 타겟 시 역할 코드 필수
        String targetType = request.targetType() != null ? request.targetType() : "ALL";
        if ("ROLE".equals(targetType)) {
            if (request.targetRoleCodes() == null || request.targetRoleCodes().isEmpty()) {
                throw new PopupTargetMissingException();
            }
        }

        // HTML sanitize (Jsoup — OWASP XSS 방어)
        String sanitized = Jsoup.clean(request.contentHtml(), Safelist.relaxed());

        Popup popup = Popup.builder()
                .siteId(request.siteId())
                .title(request.title())
                .contentHtml(sanitized)
                .position(request.position() != null ? request.position() : "CENTER")
                .xOffset(request.xOffset())
                .yOffset(request.yOffset())
                .width(request.width() != null ? request.width() : 400)
                .height(request.height() != null ? request.height() : 300)
                .showFrom(request.showFrom())
                .showUntil(request.showUntil())
                .showTodayClose(request.showTodayClose() != null ? request.showTodayClose() : true)
                .displayPriority(request.displayPriority() != null ? request.displayPriority() : 0)
                .targetType(targetType)
                .targetRoleCodes(request.targetRoleCodes() != null ? request.targetRoleCodes() : Collections.emptyList())
                .status("ACTIVE")
                .build();

        popupMapper.insert(popup);

        // show_today_close=true 이면 쿠키 키 생성
        if (popup.isShowTodayClose()) {
            popup.setCookieKey("popup-close-" + popup.getId());
        }

        return PopupResponse.from(popup);
    }

    /**
     * 팝업 수정.
     */
    @Override
    @Transactional
    @CacheEvict(value = "popupActive", allEntries = true)
    public PopupResponse updatePopup(Long id, PopupRequest request) {
        Popup existing = popupMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다. id=" + id));

        // 기간 검증
        if (!request.showFrom().isBefore(request.showUntil())) {
            throw new PopupPeriodInvalidException();
        }

        String targetType = request.targetType() != null ? request.targetType() : existing.getTargetType();
        if ("ROLE".equals(targetType)) {
            if (request.targetRoleCodes() == null || request.targetRoleCodes().isEmpty()) {
                throw new PopupTargetMissingException();
            }
        }

        String sanitized = Jsoup.clean(request.contentHtml(), Safelist.relaxed());

        existing.setTitle(request.title());
        existing.setContentHtml(sanitized);
        if (request.position() != null) existing.setPosition(request.position());
        existing.setShowFrom(request.showFrom());
        existing.setShowUntil(request.showUntil());
        if (request.showTodayClose() != null) existing.setShowTodayClose(request.showTodayClose());
        if (request.displayPriority() != null) existing.setDisplayPriority(request.displayPriority());
        existing.setTargetType(targetType);
        if (request.targetRoleCodes() != null) existing.setTargetRoleCodes(request.targetRoleCodes());

        popupMapper.update(existing);
        return PopupResponse.from(existing);
    }

    /**
     * 팝업 삭제.
     */
    @Override
    @Transactional
    @CacheEvict(value = "popupActive", allEntries = true)
    public void deletePopup(Long id) {
        popupMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다. id=" + id));
        popupMapper.deleteById(id);
    }

    /**
     * 팝업 활성/비활성 토글.
     */
    @Override
    @Transactional
    @CacheEvict(value = "popupActive", allEntries = true)
    public PopupResponse setActive(Long id, boolean active) {
        Popup popup = popupMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다. id=" + id));
        String newStatus = active ? "ACTIVE" : "INACTIVE";
        popupMapper.updateStatus(id, newStatus);
        popup.setStatus(newStatus);
        return PopupResponse.from(popup);
    }

    /**
     * 사이트별 전체 팝업 목록 (관리자용).
     */
    @Override
    public List<PopupResponse> getPopupsBySite(Long siteId) {
        return popupMapper.findBySiteId(siteId).stream()
                .map(PopupResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 활성 팝업 목록 조회.
     * REQ-CONTENT-008-D-2: status=ACTIVE AND show_from <= now <= show_until
     * REQ-CONTENT-008-D-3: display_priority DESC, 상위 5개 제한
     */
    @Override
    @Cacheable(value = "popupActive", key = "#siteId")
    public List<PopupActiveResponse> getActivePopups(Long siteId) {
        Instant now = Instant.now();
        List<Popup> actives = popupMapper.findActiveByTimeWindow(siteId, now);

        // display_priority DESC 정렬 후 상위 5개 제한
        return actives.stream()
                .sorted((a, b) -> Integer.compare(b.getDisplayPriority(), a.getDisplayPriority()))
                .limit(POPUP_LIMIT)
                .map(popup -> {
                    // show_today_close=true 이면 cookieKey 설정
                    if (popup.isShowTodayClose() && popup.getCookieKey() == null) {
                        popup.setCookieKey("popup-close-" + popup.getId());
                    }
                    return PopupActiveResponse.from(popup);
                })
                .collect(Collectors.toList());
    }
}
