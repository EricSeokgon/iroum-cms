package kr.co.ircp.cms.domain.content.i18n.service;

import kr.co.ircp.cms.domain.content.i18n.dto.I18nResourceItem;
import kr.co.ircp.cms.domain.content.i18n.dto.I18nResponse;
import kr.co.ircp.cms.domain.content.i18n.entity.I18nResource;
import kr.co.ircp.cms.domain.content.i18n.mapper.I18nResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 다국어 리소스 리졸버 구현체.
 * REQ-CONTENT-010-D-2: 언어 폴백 체인 (요청 언어 → ko)
 *
 * // @MX:ANCHOR: [AUTO] I18nResolverImpl — 다국어 폴백 구현
 * // @MX:REASON: I18nController에서 fan_in >= 3으로 참조
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class I18nResolverImpl implements I18nResolver {

    /** 지원 네임스페이스 (chk_i18n_namespace CHECK 제약과 동일) */
    private static final Set<String> SUPPORTED_NAMESPACES = Set.of(
            "menu", "page", "popup", "banner", "content_block", "system"
    );

    /** 지원 언어 (chk_i18n_language CHECK 제약과 동일) */
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ko", "en");

    /** 최종 폴백 언어 */
    private static final String FALLBACK_LANGUAGE = "ko";

    /** 사이트 기본 언어 (단순화: 실제 구현에서는 SiteService를 주입받아 조회) */
    private static final String DEFAULT_SITE_LANGUAGE = "ko";

    private final I18nResourceMapper i18nResourceMapper;

    /**
     * 폴백 체인으로 다국어 필드 해석.
     * REQ-CONTENT-010-D-2: 요청 언어 → site.default_language → 'ko'
     */
    @Override
    public I18nResponse resolveFields(String namespace, Long resourceId, String language) {
        // 지원 네임스페이스/언어 검증
        if (!SUPPORTED_NAMESPACES.contains(namespace)) {
            throw new IllegalArgumentException("지원하지 않는 네임스페이스입니다. namespace=" + namespace);
        }
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("지원하지 않는 언어입니다. language=" + language);
        }

        // 1단계: 요청 언어로 조회
        Map<String, String> fields = toFieldMap(
                i18nResourceMapper.findByNamespaceAndResourceIdAndLanguage(namespace, resourceId, language)
        );

        // 2단계: site.default_language로 폴백 (요청 언어 != 기본 언어일 때)
        if (!language.equals(DEFAULT_SITE_LANGUAGE)) {
            Map<String, String> defaultFields = toFieldMap(
                    i18nResourceMapper.findByNamespaceAndResourceIdAndLanguage(namespace, resourceId, DEFAULT_SITE_LANGUAGE)
            );
            // 요청 언어에 없는 필드는 기본 언어에서 채움
            defaultFields.forEach(fields::putIfAbsent);
        }

        // 3단계: 'ko' 폴백 (기본 언어도 'ko'가 아닐 경우)
        if (!FALLBACK_LANGUAGE.equals(language) && !FALLBACK_LANGUAGE.equals(DEFAULT_SITE_LANGUAGE)) {
            Map<String, String> koFields = toFieldMap(
                    i18nResourceMapper.findByNamespaceAndResourceIdAndLanguage(namespace, resourceId, FALLBACK_LANGUAGE)
            );
            koFields.forEach(fields::putIfAbsent);
        }

        return new I18nResponse(namespace, resourceId, language, fields);
    }

    /**
     * 다국어 리소스 배치 upsert.
     * REQ-CONTENT-010-D: UNIQUE (namespace, resource_id, language, field_name) ON CONFLICT UPDATE
     */
    @Override
    @Transactional
    public void bulkUpsert(List<I18nResourceItem> items) {
        // 네임스페이스/언어 검증
        for (I18nResourceItem item : items) {
            if (!SUPPORTED_NAMESPACES.contains(item.namespace())) {
                throw new IllegalArgumentException("지원하지 않는 네임스페이스입니다. namespace=" + item.namespace());
            }
            if (!SUPPORTED_LANGUAGES.contains(item.language())) {
                throw new IllegalArgumentException("지원하지 않는 언어입니다. language=" + item.language());
            }
        }

        List<I18nResource> resources = items.stream()
                .map(item -> I18nResource.builder()
                        .namespace(item.namespace())
                        .resourceId(item.resourceId())
                        .language(item.language())
                        .fieldName(item.fieldName())
                        .value(item.value())
                        .build())
                .collect(Collectors.toList());

        i18nResourceMapper.upsertBatch(resources);
    }

    // ─── private helpers ───────────────────────────────────────────────────────

    private Map<String, String> toFieldMap(List<I18nResource> resources) {
        Map<String, String> map = new HashMap<>();
        for (I18nResource r : resources) {
            map.put(r.getFieldName(), r.getValue());
        }
        return map;
    }
}
