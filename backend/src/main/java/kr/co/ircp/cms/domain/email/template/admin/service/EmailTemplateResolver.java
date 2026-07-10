package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplate;
import kr.co.ircp.cms.domain.email.template.admin.repository.EmailTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 이메일 템플릿 조회·렌더링 단일 진입점.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-030/033 — 활성 템플릿이 있으면 렌더링 결과를,
 * 없거나 렌더링에 실패하면 {@code Optional.empty()}를 반환한다. 호출 측(EmailServiceImpl,
 * QnaNotificationServiceImpl)은 빈 결과일 때 기존 하드코딩 문구로 fallback 한다.
 *
 * <p>핵심 계약: <b>이 메서드는 예외를 전파하지 않는다.</b> 어떤 실패든 빈 Optional로 흡수하여
 * 기존 발송 동작이 깨지지 않도록 보장한다(회귀 방지).
 */
// @MX:ANCHOR: [AUTO] resolveAndRender — 이메일 템플릿 조회·렌더링 단일 진입점
// @MX:REASON: fan_in >= 3 (EmailServiceImpl, QnaNotificationServiceImpl, EmailTemplateServiceImpl 미리보기/테스트발송). 예외 비전파 계약으로 기존 발송 회귀 방지
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001#REQ-ET-030
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateResolver {

    private final EmailTemplateMapper templateMapper;
    private final EmailTemplateRenderer renderer;

    /**
     * 코드+언어로 활성 템플릿을 찾아 변수맵으로 렌더링한다.
     *
     * @return 활성 템플릿이 있고 렌더링에 성공하면 결과, 아니면 {@code Optional.empty()}
     */
    public Optional<RenderResult> resolveAndRender(String code, String language, Map<String, Object> vars) {
        try {
            String lang = (language == null || language.isBlank()) ? "ko" : language;
            Optional<EmailTemplate> found = templateMapper.findActiveByCodeAndLanguage(code, lang);
            if (found.isEmpty()) {
                // 활성 템플릿 없음 → 호출 측이 하드코딩 fallback
                return Optional.empty();
            }
            EmailTemplate template = found.get();
            RenderResult result = renderer.render(
                    template.getSubject(),
                    template.getBodyHtml(),
                    template.getBodyText(),
                    vars,
                    template.getVariables());
            return Optional.of(result);
        } catch (Exception e) {
            // 렌더링/조회 실패는 fallback 신호로 흡수 (REQ-ET-033 — 절대 예외 전파 금지)
            log.warn("이메일 템플릿 렌더링 실패 — 하드코딩 fallback: code={}, lang={}, error={}",
                    code, language, e.getMessage());
            return Optional.empty();
        }
    }
}
