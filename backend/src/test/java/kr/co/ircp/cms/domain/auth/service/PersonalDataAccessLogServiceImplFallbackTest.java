package kr.co.ircp.cms.domain.auth.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessPurpose;
import kr.co.ircp.cms.domain.auth.repository.PersonalDataAccessLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SPEC-CMS-SECURITY-PII-FOLLOWUP-002 RUN — PersonalDataAccessLogServiceImpl fallback unit test.
 *
 * <p>본 unit test는 PII-FOLLOWUP-001 PiiAuditEnhanceIT의 AC-FU-003-2 (audit insertion failure
 * simulation)를 옵션 B 채택 따라 Spring context 외부로 분리한 것이다.
 *
 * <p><b>분리 사유</b>:
 * <ul>
 *   <li>PersonalDataAccessLogService.recordBulk는 @Async("auditExecutor")로 Spring AOP CGLIB
 *       proxy로 wrap된다. @MockitoSpyBean으로 spy 시도 시 "Following methods cannot be
 *       stubbed/verified: ... non-public parent classes" 에러 발생.</li>
 *   <li>본 unit test는 PersonalDataAccessLogServiceImpl을 직접 생성하여 AOP proxy를 우회한다.
 *       @Async 어노테이션은 무시되고 real method가 동기 호출되어 try-catch fallback이 검증된다.</li>
 *   <li>Spring context 불필요 → Testcontainers PostgreSQL 부팅 없음 → 빠른 실행 (~수백 ms).</li>
 * </ul>
 *
 * <p><b>검증 항목</b>:
 * <ul>
 *   <li>AC-FU-003-2 (← PII-FOLLOWUP-001 AC-009-5): mapper.insert가 DataAccessException 발생 시
 *       recordBulk가 예외를 호출자에게 전파하지 않고 silently 처리한다 (try-catch fallback).</li>
 *   <li>AC-FU2-001-3: 실패 시 Micrometer counter {@code pii.audit.log.failure.count}가 1 증가한다.</li>
 * </ul>
 *
 * <p>관련 SPEC: SPEC-CMS-SECURITY-PII-FOLLOWUP-002 (REQ-PII-FU2-001/002)
 */
// @MX:NOTE: [AUTO] PersonalDataAccessLogServiceImplFallbackTest — @Async/Spy 충돌 우회 unit test
// @MX:SPEC: SPEC-CMS-SECURITY-PII-FOLLOWUP-002
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalDataAccessLogServiceImpl fallback unit test (SPEC-CMS-SECURITY-PII-FOLLOWUP-002)")
class PersonalDataAccessLogServiceImplFallbackTest {

    @Mock
    private PersonalDataAccessLogMapper mapper;

    @Mock
    private PlatformTransactionManager txManager;

    private MeterRegistry meterRegistry;
    private PersonalDataAccessLogServiceImpl service;

    @BeforeEach
    void setUp() {
        // SimpleMeterRegistry — Micrometer counter 카운트 검증용 (Spring context 불필요)
        meterRegistry = new SimpleMeterRegistry();
        service = new PersonalDataAccessLogServiceImpl(mapper, meterRegistry, txManager);
    }

    /**
     * AC-FU-003-2 (← PII-FOLLOWUP-001 AC-009-5): recordBulk DataAccessException 주입 시
     * 예외 미전파 + Micrometer counter 1 증가.
     *
     * <p>운영 정책 (REQ-PII-EMAIL-009): audit log 적재 실패 시 user-facing 에러를 발생시키지 않고
     * 백그라운드에서 ERROR 로그 + Micrometer counter로 알림. user 응답에는 영향 없음.
     */
    @Test
    @DisplayName("AC-FU-003-2: mapper.insert DataAccessException → 예외 미전파 + counter 1 증가 (fallback)")
    void recordBulk_insertFailure_doesNotPropagateError_andIncrementsCounter() {
        // given: mapper.insert가 DataAccessException을 throw하도록 stub
        doThrow(new DataAccessException("시뮬레이션: audit INSERT 실패") {})
                .when(mapper).insert(org.mockito.ArgumentMatchers.any());

        long counterBefore = (long) meterRegistry.counter("pii.audit.log.failure.count").count();

        // when: recordBulk 호출 (5건 target)
        assertThatCode(() -> service.recordBulk(
                1L, "SUPER_ADMIN",
                List.of(10L, 20L, 30L, 40L, 50L),
                Set.of("email"),
                PersonalDataAccessPurpose.ADMIN_USER_LIST
        ))
                .as("recordBulk는 mapper INSERT 실패 시 예외를 호출자에게 전파하지 않아야 함")
                .doesNotThrowAnyException();

        // then: try-catch가 루프 안에 있으므로 5건 모두 시도됨 (각 실패는 독립적으로 처리)
        verify(mapper, times(5)).insert(org.mockito.ArgumentMatchers.any());

        // then: Micrometer counter pii.audit.log.failure.count 5 증가 (각 row 실패마다 1씩)
        long counterAfter = (long) meterRegistry.counter("pii.audit.log.failure.count").count();
        assertThat(counterAfter - counterBefore)
                .as("5건 모두 INSERT 실패 시 pii.audit.log.failure.count가 5 증가해야 함 (REQ-PII-EMAIL-009)")
                .isEqualTo(5L);
    }

    /**
     * AC-FU2-001-3 (보조): 빈 targetUserIds → mapper.insert 호출 없이 정상 return.
     */
    @Test
    @DisplayName("AC-FU2-001-3: 빈 targetUserIds → mapper.insert 호출 안 함")
    void recordBulk_emptyTargets_skipsInsert() {
        assertThatCode(() -> service.recordBulk(
                1L, "SUPER_ADMIN",
                List.of(),
                Set.of("email"),
                PersonalDataAccessPurpose.ADMIN_USER_LIST
        )).doesNotThrowAnyException();

        verify(mapper, times(0)).insert(org.mockito.ArgumentMatchers.any());
    }

    /**
     * AC-FU2-001-4 (보조): 정상 시나리오 — 5건 target → mapper.insert 5회 호출, counter 미증가.
     */
    @Test
    @DisplayName("AC-FU2-001-4: 정상 5건 target → mapper.insert 5회 + counter 미증가")
    void recordBulk_5targets_insertsAllAndDoesNotIncrementCounter() {
        long counterBefore = (long) meterRegistry.counter("pii.audit.log.failure.count").count();

        service.recordBulk(
                1L, "SUPER_ADMIN",
                List.of(10L, 20L, 30L, 40L, 50L),
                Set.of("email"),
                PersonalDataAccessPurpose.ADMIN_USER_LIST
        );

        verify(mapper, times(5)).insert(org.mockito.ArgumentMatchers.any());

        long counterAfter = (long) meterRegistry.counter("pii.audit.log.failure.count").count();
        assertThat(counterAfter)
                .as("정상 시나리오에서는 failure counter 증가하지 않아야 함")
                .isEqualTo(counterBefore);
    }
}
