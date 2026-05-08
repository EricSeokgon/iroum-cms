package kr.co.ircp.cms.domain.auth.service;

import io.micrometer.core.instrument.MeterRegistry;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PersonalDataAccessEntry;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessLog;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessPurpose;
import kr.co.ircp.cms.domain.auth.repository.PersonalDataAccessLogMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 개인정보 접근 로그 서비스 구현체.
 *
 * <p>REQ-AUTH-018-D-1~4 — 비동기 적재(@Async auditExecutor), 관리자/본인 조회.
 * DB APPEND-ONLY 트리거로 수정·삭제가 차단되므로 서비스 레이어에서 별도 검증 불요.
 */
// @MX:WARN: [AUTO] PersonalDataAccessLogServiceImpl.record — @Async 비동기; SecurityContext는 전파되지 않을 수 있음
// @MX:REASON: @Async(auditExecutor)는 별도 스레드 풀에서 실행되므로 SecurityContext 공유 불가. 모든 필요 값(viewerId, role, MDC)을 호출 시점에 추출해 파라미터로 전달해야 한다.
@Service
@RequiredArgsConstructor
public class PersonalDataAccessLogServiceImpl implements PersonalDataAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataAccessLogServiceImpl.class);

    private static final Set<String> VALID_SORT = Set.of(
            "accessedAt,desc", "accessedAt,asc"
    );

    private final PersonalDataAccessLogMapper mapper;
    private final MeterRegistry meterRegistry;

    /**
     * 개인정보 접근 로그 비동기 적재.
     *
     * <p>MDC 값(ipAddress, userAgent, traceId)은 @Async 진입 전 호출 스레드에서 캡처된다.
     * Spring의 기본 설정에서는 MDC가 자식 스레드로 전파되지 않으므로, 필요 시
     * TaskDecorator로 MDC 전파를 구성해야 한다 (현재는 AspectJ join point에서 직접 추출).
     */
    @Override
    @Async("auditExecutor")
    public void record(long viewerId, String viewerRole, long targetUserId,
                       Set<String> accessedFields, PersonalDataAccessPurpose purpose) {
        try {
            // MDC 값 추출 (호출 시점 스레드에서 이미 설정된 값)
            String ipAddress = MDC.get("ipAddress");
            String userAgent = MDC.get("userAgent");
            String traceId   = MDC.get("traceId");

            PersonalDataAccessLog logEntry = PersonalDataAccessLog.builder()
                    .viewerId(viewerId)
                    .viewerRole(viewerRole)
                    .targetUserId(targetUserId)
                    .accessedFields(new ArrayList<>(accessedFields))
                    .purpose(purpose.name())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .traceId(traceId)
                    .build();

            mapper.insert(logEntry);
        } catch (Exception e) {
            log.error("개인정보 접근 로그 적재 실패 (non-blocking, REQ-AUTH-018)", e);
        }
    }

    /**
     * N건 대상 일괄 개인정보 접근 로그 비동기 적재.
     *
     * <p>REQ-PII-EMAIL-009 — findPage(actor) 결과 N건 일괄 INSERT.
     * 적재 실패 시 user-facing 에러 미전파: ERROR 로그 + Micrometer counter 증가.
     */
    // @MX:WARN: [AUTO] recordBulk — @Async 비동기; SecurityContext 미전파, 파라미터로 모든 값 전달 필수
    // @MX:REASON: @Async(auditExecutor) 별도 스레드 풀; SecurityContext 공유 불가 (기존 record()와 동일 패턴)
    @Override
    @Async("auditExecutor")
    public void recordBulk(long viewerId, String viewerRole, List<Long> targetUserIds,
                           Set<String> accessedFields, PersonalDataAccessPurpose purpose) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return;
        }
        try {
            String ipAddress = MDC.get("ipAddress");
            String userAgent = MDC.get("userAgent");
            String traceId   = MDC.get("traceId");

            for (Long targetUserId : targetUserIds) {
                PersonalDataAccessLog logEntry = PersonalDataAccessLog.builder()
                        .viewerId(viewerId)
                        .viewerRole(viewerRole)
                        .targetUserId(targetUserId)
                        .accessedFields(new ArrayList<>(accessedFields))
                        .purpose(purpose.name())
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .traceId(traceId)
                        .build();
                mapper.insert(logEntry);
            }
        } catch (Exception e) {
            log.error("PII audit log INSERT failed (bulk, non-blocking, REQ-PII-EMAIL-009)", e);
            meterRegistry.counter("pii.audit.log.failure.count").increment();
        }
    }

    // @MX:ANCHOR: [AUTO] findPage — 관리자용 개인정보 접근 이력 검색 진입점
    // @MX:REASON: PersonalDataAccessController, 테스트 Mock, PersonalDataAccessLogService 참조 (fan_in >= 3)
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PersonalDataAccessEntry> findPage(int page, int size, String sort,
                                                           Long targetUserId, Long viewerId, String purpose,
                                                           Instant from, Instant to) {
        String safeSort = VALID_SORT.contains(sort) ? sort : "accessedAt,desc";
        int offset = page * size;

        List<PersonalDataAccessEntry> content = mapper.findPage(
                offset, size, targetUserId, viewerId, purpose, from, to, safeSort);
        long total = mapper.countAll(targetUserId, viewerId, purpose, from, to);

        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PersonalDataAccessEntry> findByTarget(long targetUserId, int page, int size) {
        int offset = page * size;

        List<PersonalDataAccessEntry> content = mapper.findByTarget(targetUserId, offset, size);
        long total = mapper.countByTarget(targetUserId);

        return PageResponse.of(content, page, size, total);
    }
}
