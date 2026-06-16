package kr.co.ircp.cms.domain.email.template.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * 이메일 템플릿 렌더링용 Thymeleaf 엔진 설정.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-010 — DB에 저장된 템플릿 문자열을
 * {@code ${변수명}} 자리표시자 치환으로 렌더링한다. 템플릿이 클래스패스 파일이 아니라
 * DB 컬럼(subject, body_html)에서 오므로 {@link StringTemplateResolver}를 사용한다.
 *
 * <p><b>중요:</b> 빈 타입을 {@link ITemplateEngine}으로 노출한다. Spring Boot의
 * ThymeleafAutoConfiguration이 MVC 뷰 리졸버용으로 {@code SpringTemplateEngine} 단일 빈을
 * 기대하므로, 구현체 타입으로 노출하면 NoUniqueBeanDefinitionException으로 컨텍스트가 깨진다.
 *
 * <ul>
 *   <li>{@code emailSubjectTemplateEngine}: TEXT 모드 — 제목(평문) 치환</li>
 *   <li>{@code emailHtmlTemplateEngine}: HTML 모드 — 본문(HTML) 치환</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] SPEC-CMS-EMAIL-TEMPLATE-001 — 빈 타입은 ITemplateEngine. SpringTemplateEngine으로 노출 시 MVC 뷰리졸버와 충돌
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001#REQ-ET-010
@Configuration
public class EmailTemplateConfig {

    /** 캐시 미사용 — 템플릿 문자열이 매 호출마다 다르므로 캐싱은 의미가 없고 메모리만 누수한다. */
    private static final boolean CACHEABLE = false;

    /** 제목 렌더링 엔진 (TEXT 모드, 평문 치환). SpringEL 사용(OGNL 미의존). */
    @Bean
    public ITemplateEngine emailSubjectTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(stringResolver(TemplateMode.TEXT));
        return engine;
    }

    /** 본문 렌더링 엔진 (HTML 모드). SpringEL 사용(OGNL 미의존). */
    @Bean
    public ITemplateEngine emailHtmlTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(stringResolver(TemplateMode.HTML));
        return engine;
    }

    private StringTemplateResolver stringResolver(TemplateMode mode) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(mode);
        resolver.setCacheable(CACHEABLE);
        return resolver;
    }
}
