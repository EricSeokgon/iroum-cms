import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

group = "kr.co.ircp"
version = "0.1.0-SNAPSHOT"

// Java 17 toolchain — Gradle이 Foojay API를 통해 자동 다운로드
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    // egovFrame 5.0 공식 Maven 저장소
    // 접근 불가 시 egovFrame 의존성이 해결되지 않으며, 하단 TODO 참조
    maven {
        url = uri("https://maven.egovframe.go.kr/maven/")
        isAllowInsecureProtocol = false
    }
}

// ─── 의존성 버전 상수 ─────────────────────────────────────────────────────
val mybatisStarterVersion = "3.0.4"
val jjwtVersion = "0.12.7"
val springdocVersion = "2.8.17"
val testcontainersVersion = "1.20.4"

dependencies {
    // ─── Spring Boot Starters ──────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // AOP — AspectJ 위빙 지원 (@AuditLog Aspect, SPEC-CMS-005 §7)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // ─── Caffeine 캐시 (REQ-CONTENT-007-D-3) ─────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // ─── Micrometer Prometheus (REQ-SYSTEM-006-D) ────────────────────────────
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ─── Spring Retry (REQ-SYSTEM-002-D 배치 재시도) ──────────────────────────
    implementation("org.springframework.retry:spring-retry")

    // ─── MyBatis ──────────────────────────────────────────────────────────
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:$mybatisStarterVersion")

    // ─── PostgreSQL JDBC ───────────────────────────────────────────────────
    // PGobject 등 컴파일 타임 사용 클래스가 있으므로 implementation
    implementation("org.postgresql:postgresql")

    // ─── Flyway ───────────────────────────────────────────────────────────
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // ─── JWT (jjwt 0.12.7) ────────────────────────────────────────────────
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // ─── OpenAPI / Swagger UI ─────────────────────────────────────────────
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // ─── Lombok ───────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ─── egovFrame 5.0.0 ──────────────────────────────────────────────────
    // TODO(SPEC-CMS-002): egovFrame Maven 저장소 접근 가능 여부 확인 후 주석 해제
    //   저장소: https://maven.egovframe.go.kr/maven/
    //   접근 불가 시 공식 배포 JAR을 libs/ 폴더에 직접 배치하거나,
    //   Spring 표준 의존성(JDBC, Transaction, AOP)으로 대체 구현 후 SPEC-CMS-002에서 통합.
    //
    // implementation("org.egovframe.rte:egovframework.rte.psl.dataaccess:5.0.0")
    // implementation("org.egovframe.rte:egovframework.rte.fdl.cmmn:5.0.0")
    // implementation("org.egovframe.rte:egovframework.rte.fdl.security:5.0.0")

    // egovFrame 대체 — 표준 Spring 의존성 (Step 0 bootstrap)
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework:spring-tx")

    // ─── Test ──────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // Mockito는 별도로 추가하므로 제외하지 않음 (spring-boot-starter-test가 이미 포함)
    }
    testImplementation("org.springframework.security:spring-security-test")

    // Testcontainers — 실제 PostgreSQL 컨테이너 기반 통합 테스트
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    // @ServiceConnection 자동 DataSource 주입 (Spring Boot 3.1+, SPEC-CMS-002 IT)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // ArchUnit — 아키텍처 규칙 단위 테스트 (REQ-PII-EMAIL-008 @JsonSerialize 강제 검증)
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // Awaitility — 비동기 검증 대기 헬퍼 (SPEC-CMS-SECURITY-PII-002 RUN follow-up — @Async audit 적재 검증용)
    testImplementation("org.awaitility:awaitility:4.2.2")

    // Lombok (테스트 코드에서도 사용)
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // ─── Logback JSON 인코더 (REQ-CROSS-007-D-2 — 운영 프로파일 JSON 로그) ─────────
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // ─── HTML Sanitizer (SPEC-CMS-004, REQ-CONTENT-006-D-1) ──────────────────
    // OWASP XSS 방어: RICH_TEXT / MARKDOWN / IMAGE 블록 payload sanitize용
    implementation("org.jsoup:jsoup:1.17.2")

    // ─── 미디어 라이브러리 (SPEC-CMS-MEDIA-001) ───────────────────────────────
    // Apache Tika: 매직넘버 기반 MIME 검증 (REQ-MEDIA-001-D-5)
    implementation("org.apache.tika:tika-core:2.9.2")
    // Apache Commons Imaging: EXIF 메타데이터 제거 (REQ-MEDIA-002-D-1)
    implementation("org.apache.commons:commons-imaging:1.0.0-alpha5")
    // imgscalr: 썸네일 생성 (REQ-MEDIA-002-D-3)
    implementation("org.imgscalr:imgscalr-lib:4.2")

    // ─── Apache POI (SPEC-CMS-008 REQ-VIZ-006-D-1, 엑셀 스트리밍 export) ────────
    // SXSSFWorkbook 으로 100만 행 OOM 없이 청크 단위 시트 작성.
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
}

// ─── 빌드 설정 ────────────────────────────────────────────────────────────
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("iroum-cms.jar")
}

// REQ-AUTH-018 — @PersonalDataAccess AOP: AspectJ 파라미터명 추출을 위해 -parameters 옵션 필요
// PersonalDataAccessAspect.extractTargetUserId()가 Parameter.getName()을 사용함
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

// ─── 테스트 설정 ──────────────────────────────────────────────────────────
tasks.test {
    useJUnitPlatform {
        // @Tag("integration") 태그 제외 — Docker 미설치 환경에서 컨테이너 기동 실패 방지
        // 통합 테스트 실행: ./gradlew integrationTest
        excludeTags("integration")
    }
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

// ─── 통합 테스트 task (Docker 필요) ─────────────────────────────────────
tasks.register<Test>("integrationTest") {
    description = "Testcontainers 기반 통합 테스트 실행 (Docker 필요)"
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }
    shouldRunAfter(tasks.test)
}

// ─── JaCoCo 커버리지 ──────────────────────────────────────────────────────
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    // 커버리지 제외 대상 (생성 코드, 설정 클래스 등)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/IroumCmsApplication.class",
                    "**/config/**",
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
