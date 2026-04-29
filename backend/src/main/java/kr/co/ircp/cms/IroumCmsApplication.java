package kr.co.ircp.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * iroum-cms 백엔드 진입점.
 *
 * <p>egovFrame 5.0 / Spring Boot 3.2 기반 CMS API 서버.
 * 환경 프로파일은 SPRING_PROFILES_ACTIVE 환경변수 또는
 * -Dspring.profiles.active 옵션으로 선택한다 (local|dev|prod).
 */
@SpringBootApplication
public class IroumCmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(IroumCmsApplication.class, args);
    }
}
