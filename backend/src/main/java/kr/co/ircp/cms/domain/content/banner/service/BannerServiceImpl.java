package kr.co.ircp.cms.domain.content.banner.service;

import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.content.banner.dto.BannerRequest;
import kr.co.ircp.cms.domain.content.banner.dto.BannerResponse;
import kr.co.ircp.cms.domain.content.banner.entity.Banner;
import kr.co.ircp.cms.domain.content.banner.exception.BannerAltTextMissingException;
import kr.co.ircp.cms.domain.content.banner.exception.BannerPeriodInvalidException;
import kr.co.ircp.cms.domain.content.banner.mapper.BannerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 배너 서비스 구현체.
 * REQ-CONTENT-009-D: 배너 CRUD + 클릭 카운트 + 활성 배너 조회
 *
 * // @MX:ANCHOR: [AUTO] BannerServiceImpl — 배너 전체 라이프사이클 관리
 * // @MX:REASON: BannerController에서 fan_in >= 3으로 참조
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;
    private final AuditLogService auditLogService;

    /**
     * 배너 등록.
     * REQ-CONTENT-009-D-1: display_from < display_until 검증, alt_text NOT NULL 검증 (KWCAG 1.1.1)
     */
    @Override
    @Transactional
    public BannerResponse registerBanner(BannerRequest request) {
        // alt_text 필수 검증 (KWCAG 2.2 AA 1.1.1)
        if (request.altText() == null || request.altText().isBlank()) {
            throw new BannerAltTextMissingException();
        }

        // 기간 검증
        if (!request.displayFrom().isBefore(request.displayUntil())) {
            throw new BannerPeriodInvalidException();
        }

        Banner banner = Banner.builder()
                .siteId(request.siteId())
                .bannerGroupCode(request.bannerGroupCode())
                .title(request.title())
                .imageUrl(request.imageUrl())
                .linkUrl(request.linkUrl())
                .linkTarget(request.linkTarget() != null ? request.linkTarget() : "_self")
                .altText(request.altText())
                .displayFrom(request.displayFrom())
                .displayUntil(request.displayUntil())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .clickCount(0L)
                .status("ACTIVE")
                .build();

        bannerMapper.insert(banner);
        return BannerResponse.from(banner);
    }

    /**
     * 배너 수정.
     */
    @Override
    @Transactional
    public BannerResponse updateBanner(Long id, BannerRequest request) {
        Banner existing = bannerMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("배너를 찾을 수 없습니다. id=" + id));

        if (request.altText() == null || request.altText().isBlank()) {
            throw new BannerAltTextMissingException();
        }

        if (!request.displayFrom().isBefore(request.displayUntil())) {
            throw new BannerPeriodInvalidException();
        }

        existing.setTitle(request.title());
        existing.setImageUrl(request.imageUrl());
        existing.setLinkUrl(request.linkUrl());
        existing.setAltText(request.altText());
        existing.setDisplayFrom(request.displayFrom());
        existing.setDisplayUntil(request.displayUntil());
        if (request.sortOrder() != null) existing.setSortOrder(request.sortOrder());

        bannerMapper.update(existing);
        return BannerResponse.from(existing);
    }

    /**
     * 배너 삭제.
     */
    @Override
    @Transactional
    public void deleteBanner(Long id) {
        bannerMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("배너를 찾을 수 없습니다. id=" + id));
        bannerMapper.deleteById(id);
    }

    /**
     * 그룹별 활성 배너 조회.
     * REQ-CONTENT-009-D-2: status=ACTIVE AND 시간 윈도우 필터 + sort_order ASC
     */
    @Override
    public List<BannerResponse> getActiveBannersByGroup(String bannerGroupCode) {
        Instant now = Instant.now();
        return bannerMapper.findActiveByGroupAndTimeWindow(bannerGroupCode, now)
                .stream()
                .map(BannerResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 관리자용 배너 목록 조회.
     */
    @Override
    public List<BannerResponse> listBanners(Long siteId, String groupCode) {
        String effectiveGroup = (groupCode == null || groupCode.isBlank()) ? null : groupCode;
        return bannerMapper.findAdminBySiteId(siteId, effectiveGroup)
                .stream()
                .map(BannerResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사이트별 배너 그룹 코드 목록 조회.
     */
    @Override
    public List<String> listGroups(Long siteId) {
        return bannerMapper.findGroupsBySiteId(siteId);
    }

    /**
     * 배너 클릭 이벤트 기록.
     * REQ-CONTENT-009-D-3: click_count 원자적 UPDATE + audit_log 기록
     *
     * // @MX:WARN: [AUTO] 고빈도 클릭 시 DB 락 경합 가능 — 추후 카운터 버퍼 도입 검토
     * // @MX:REASON: 원자적 UPDATE이지만 트래픽 급증 시 write amplification 발생 가능
     */
    @Override
    @Transactional
    public void recordClick(Long id) {
        bannerMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("배너를 찾을 수 없습니다. id=" + id));

        // 원자적 click_count 증가 (UPDATE banner SET click_count = click_count + 1)
        bannerMapper.incrementClickCount(id);

        // audit_log 기록
        auditLogService.record(new AuditLogService.AuditLogRecord(
                Instant.now(),
                null, null,
                "BANNER_CLICK",
                "banner",
                String.valueOf(id),
                null, null, null, null, null,
                "INFO",
                "SUCCESS",
                null, null
        ));
    }
}
