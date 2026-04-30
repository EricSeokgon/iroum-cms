package kr.co.ircp.cms.domain.content.seo.service;

import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectRequest;
import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectResponse;
import kr.co.ircp.cms.domain.content.seo.entity.SeoRedirect;
import kr.co.ircp.cms.domain.content.seo.mapper.SeoRedirectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SEO 리다이렉트 서비스 구현체.
 * REQ-CONTENT-005-D-8: URL 리다이렉트 CRUD
 *
 * // @MX:ANCHOR: [AUTO] SeoRedirectServiceImpl — SEO 리다이렉트 전체 관리
 * // @MX:REASON: SeoRedirectController, PageServiceImpl에서 fan_in >= 3으로 참조
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeoRedirectServiceImpl implements SeoRedirectService {

    /** 허용 HTTP 상태 코드 (chk_redirect_status CHECK 제약과 동일) */
    private static final Set<Short> ALLOWED_STATUS = Set.of((short) 301, (short) 302);

    private final SeoRedirectMapper seoRedirectMapper;

    @Override
    public List<SeoRedirectResponse> getAllRedirects() {
        return seoRedirectMapper.findAll().stream()
                .map(SeoRedirectResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SeoRedirectResponse> getActiveRedirectByFromPath(String fromPath) {
        return seoRedirectMapper.findActiveByFromPath(fromPath)
                .map(SeoRedirectResponse::from);
    }

    /**
     * 리다이렉트 생성.
     * REQ-CONTENT-005-D-8: 301 기본값, chk_redirect_status 검증
     */
    @Override
    @Transactional
    public SeoRedirectResponse createRedirect(SeoRedirectRequest request) {
        short status = request.httpStatus() != null ? request.httpStatus() : 301;
        if (!ALLOWED_STATUS.contains(status)) {
            throw new IllegalArgumentException("허용되지 않는 HTTP 상태 코드입니다. status=" + status + " (301/302만 허용)");
        }

        SeoRedirect redirect = SeoRedirect.builder()
                .fromPath(request.fromPath())
                .toPath(request.toPath())
                .httpStatus(status)
                .active(true)
                .reason(request.reason())
                .build();

        seoRedirectMapper.upsert(redirect);
        return SeoRedirectResponse.from(redirect);
    }

    /**
     * slug 변경 시 자동 upsert (PageServiceImpl에서 호출).
     * REQ-CONTENT-005-D-8
     */
    @Override
    @Transactional
    public void upsertFromSlugChange(String oldPath, String newPath, String reason) {
        SeoRedirect redirect = SeoRedirect.builder()
                .fromPath(oldPath)
                .toPath(newPath)
                .httpStatus((short) 301)
                .active(true)
                .reason(reason)
                .build();
        seoRedirectMapper.upsert(redirect);
    }

    /**
     * 리다이렉트 비활성화.
     */
    @Override
    @Transactional
    public void deactivateRedirect(Long id) {
        int affected = seoRedirectMapper.deactivate(id);
        if (affected == 0) {
            throw new IllegalArgumentException("리다이렉트를 찾을 수 없습니다. id=" + id);
        }
    }

    /**
     * 리다이렉트 삭제.
     */
    @Override
    @Transactional
    public void deleteRedirect(Long id) {
        int affected = seoRedirectMapper.deleteById(id);
        if (affected == 0) {
            throw new IllegalArgumentException("리다이렉트를 찾을 수 없습니다. id=" + id);
        }
    }
}
