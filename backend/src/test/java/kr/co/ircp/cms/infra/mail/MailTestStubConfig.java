package kr.co.ircp.cms.infra.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * {@code test} 프로파일 전용 {@link JavaMailSender} 스텁 구성.
 *
 * <p>SPEC-CMS-TEST-INFRA-CONTEXT-RESTORE-001 — {@code test} 프로파일의
 * {@code application.properties}에는 {@code spring.mail.host}가 없어 Spring Boot의
 * {@code MailSenderAutoConfiguration}이 {@link JavaMailSender} 빈을 생성하지 않는다.
 * 그 결과 {@code EmailServiceImpl}(JavaMailSender 의존 → VerificationServiceImpl →
 * AuthServiceImpl → AuthController)을 적재하는 모든 {@code @SpringBootTest} 컨텍스트가
 * {@code NoSuchBeanDefinitionException}으로 로드 실패한다(ML 빈 공백과 동일한 컨텍스트
 * 로드 차단 유형). 이 빈은 그 공백을 메워 컨텍스트가 정상 로드되도록 한다.
 *
 * <p>설계:
 * <ul>
 *   <li>{@code @Profile("test")} — {@code test} 프로파일에서만 활성. {@code integration}
 *       프로파일은 {@code application-integration.yml}이 {@code spring.mail.host: localhost}를
 *       제공해 운영 자동구성으로 {@link JavaMailSender}가 생성되므로, 본 스텁은 비활성이며
 *       해당 경로에 무영향이다.</li>
 *   <li>{@code @Configuration} + 운영 진입점 {@code @SpringBootApplication}(base package
 *       {@code kr.co.ircp.cms})의 컴포넌트 스캔 — {@link MlServiceClientTestStub}과 동일하게
 *       베이스 클래스 상속 여부와 무관하게 모든 {@code @SpringBootTest} 컨텍스트에 전역 등록된다.</li>
 *   <li>{@code src/test/java}에만 존재하므로 운영 jar에 포함되지 않는다.</li>
 *   <li>{@link JavaMailSenderImpl}은 실제 메일 호스트가 없어도 빈 생성에 성공한다. 메일 발송이
 *       실제로 호출되지 않는 한 호스트 부재는 문제되지 않으므로(컨텍스트 로드용 스텁 목적),
 *       추가 호스트 설정 없이 그대로 사용한다.</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] test 프로파일 전용 JavaMailSender 스텁 — @SpringBootTest 컨텍스트 로드 복구용
// @MX:NOTE: integration 프로파일은 spring.mail.host로 운영 자동구성 사용(본 스텁 비활성)
// @MX:SPEC: SPEC-CMS-TEST-INFRA-CONTEXT-RESTORE-001
@Configuration
@Profile("test")
public class MailTestStubConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl();
    }
}
