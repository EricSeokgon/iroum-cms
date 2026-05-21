package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.IncidentCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.IncidentDetail;
import kr.co.ircp.cms.domain.safety.dto.IncidentSummary;
import kr.co.ircp.cms.domain.safety.dto.IncidentUpdateRequest;
import kr.co.ircp.cms.domain.safety.dto.SyncResult;
import kr.co.ircp.cms.domain.safety.entity.SafetyIncident;
import kr.co.ircp.cms.domain.safety.exception.SafetyIncidentNotFoundException;
import kr.co.ircp.cms.domain.safety.entity.SafetyIncidentKeyword;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeyword;
import kr.co.ircp.cms.domain.safety.repository.SafetyIncidentKeywordMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyIncidentMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyKeywordMapper;
import kr.co.ircp.cms.infra.kosha.KoshaApiClient;
import kr.co.ircp.cms.infra.kosha.KoshaIncidentItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 사고사례 서비스 구현.
 * REQ-SAFETY-001
 *
 * // @MX:NOTE: [AUTO] KOSHA OpenAPI 클라이언트는 조건부 빈(@ConditionalOnProperty).
 *               service-key 미설정 시 Optional.empty() — mock 결과 반환으로 안전 폴백.
 * // @MX:SPEC: REQ-SAFETY-001-D-1, REQ-SAFETY-001-D-4
 * // @MX:NOTE: [AUTO] 키워드 추출: safety_keyword 사전 기반 토큰 매칭 (REQ-SAFETY-001-D-3).
 *                        ML 임베딩 기반 추출은 ML 서비스에 /keyword-extract 엔드포인트 추가 후 대체 가능.
 * // @MX:SPEC: REQ-SAFETY-001-D-3
 */
@Service
@Slf4j
public class SafetyIncidentServiceImpl implements SafetyIncidentService {

    private static final DateTimeFormatter KOSHA_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SafetyIncidentMapper incidentMapper;
    private final SafetyKeywordMapper keywordMapper;
    private final SafetyIncidentKeywordMapper incidentKeywordMapper;
    private final Optional<KoshaApiClient> koshaClient;

    @Autowired
    public SafetyIncidentServiceImpl(SafetyIncidentMapper incidentMapper,
                                     SafetyKeywordMapper keywordMapper,
                                     SafetyIncidentKeywordMapper incidentKeywordMapper,
                                     Optional<KoshaApiClient> koshaClient) {
        this.incidentMapper = incidentMapper;
        this.keywordMapper = keywordMapper;
        this.incidentKeywordMapper = incidentKeywordMapper;
        this.koshaClient = koshaClient;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IncidentSummary> listIncidents(
            String industryCode, String incidentType, String severity,
            int page, int size) {
        int offset = page * size;
        List<SafetyIncident> rows = incidentMapper.findFiltered(industryCode, incidentType, severity, offset, size);
        long total = incidentMapper.countFiltered(industryCode, incidentType, severity);
        List<IncidentSummary> content = rows.stream().map(this::toSummary).collect(Collectors.toList());
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentDetail getIncident(Long id) {
        SafetyIncident incident = incidentMapper.findById(id)
                .orElseThrow(() -> new SafetyIncidentNotFoundException(id));
        return toDetail(incident);
    }

    @Override
    @Transactional
    public IncidentDetail createIncident(IncidentCreateRequest request) {
        SafetyIncident incident = SafetyIncident.builder()
                .sourceType(request.sourceType())
                .industryCode(request.industryCode())
                .occupationCode(request.occupationCode())
                .processType(request.processType())
                .incidentType(request.incidentType())
                .occurredAt(request.occurredAt())
                .severity(request.severity())
                .casualties(request.casualties() == null ? 0 : request.casualties())
                .location(request.location())
                .summary(request.summary())
                .detailedCause(request.detailedCause())
                .preventionLesson(request.preventionLesson())
                .sourceUrl(request.sourceUrl())
                .status("PUBLISHED")
                .build();
        incidentMapper.insert(incident);
        return toDetail(incident);
    }

    @Override
    @Transactional
    public IncidentDetail updateIncident(Long id, IncidentUpdateRequest request) {
        SafetyIncident existing = incidentMapper.findById(id)
                .orElseThrow(() -> new SafetyIncidentNotFoundException(id));
        SafetyIncident patch = SafetyIncident.builder()
                .id(id)
                .industryCode(request.industryCode())
                .occupationCode(request.occupationCode())
                .processType(request.processType())
                .incidentType(request.incidentType())
                .occurredAt(request.occurredAt())
                .severity(request.severity())
                .casualties(request.casualties() == null ? existing.getCasualties() : request.casualties())
                .location(request.location())
                .summary(request.summary())
                .detailedCause(request.detailedCause())
                .preventionLesson(request.preventionLesson())
                .status(request.status())
                .build();
        incidentMapper.update(patch);
        return toDetail(incidentMapper.findById(id).orElseThrow());
    }

    @Override
    @Transactional
    public void archiveIncident(Long id) {
        incidentMapper.findById(id).orElseThrow(() -> new SafetyIncidentNotFoundException(id));
        incidentMapper.archiveById(id);
    }

    /**
     * 외부 동기화.
     * REQ-SAFETY-001-D-4: KOSHA OpenAPI 클라이언트가 활성화된 경우 실 호출.
     *                      미설정 환경(로컬·CI)은 mock 결과 반환으로 안전 폴백.
     */
    @Override
    @Transactional
    public SyncResult triggerExternalSync(String sourceType) {
        if ("KOSHA_OPENAPI".equals(sourceType) && koshaClient.isPresent()) {
            return syncFromKosha();
        }
        // KOSHA API key 미설정 또는 지원하지 않는 sourceType → mock 폴백
        log.info("외부 동기화 mock 반환: sourceType={}, koshaClientPresent={}",
                sourceType, koshaClient.isPresent());
        return new SyncResult(0, 0, 0, "동기화 mock 완료. sourceType=" + sourceType);
    }

    /** KOSHA OpenAPI 전 페이지 순회 동기화. */
    private SyncResult syncFromKosha() {
        KoshaApiClient client = koshaClient.get();
        int added = 0, failed = 0, pageNo = 1;

        while (true) {
            List<KoshaIncidentItem> items;
            try {
                items = client.fetchPage(pageNo);
            } catch (Exception e) {
                log.error("KOSHA API 호출 실패 (pageNo={}): {}", pageNo, e.getMessage());
                failed++;
                break;
            }
            if (items.isEmpty()) break;

            for (KoshaIncidentItem item : items) {
                try {
                    SafetyIncident incident = toIncident(item);
                    incidentMapper.insert(incident);
                    extractAndSaveKeywords(incident);
                    added++;
                } catch (Exception e) {
                    log.warn("KOSHA 사례 저장 실패: {}", e.getMessage());
                    failed++;
                }
            }
            pageNo++;
        }
        log.info("KOSHA 동기화 완료: added={}, failed={}", added, failed);
        return new SyncResult(added, 0, failed,
                String.format("KOSHA 동기화 완료: 신규 %d건, 실패 %d건", added, failed));
    }

    /** KOSHA API 응답 항목 → SafetyIncident 변환. */
    private SafetyIncident toIncident(KoshaIncidentItem item) {
        int casualties = 0;
        if (item.deathCount() != null) casualties += item.deathCount();
        if (item.injuryCount() != null) casualties += item.injuryCount();

        // 사망자 있으면 FATAL, 부상만이면 MINOR
        String severity = (item.deathCount() != null && item.deathCount() > 0) ? "FATAL" : "MINOR";

        Instant occurredAt = parseKoshaDate(item.occurredDate());

        return SafetyIncident.builder()
                .sourceType("KOSHA_OPENAPI")
                .industryCode(item.industryCode())
                .incidentType(item.incidentTypeCode())
                .occurredAt(occurredAt)
                .severity(severity)
                .casualties(casualties)
                .location(item.location())
                .summary(item.summary())
                .detailedCause(item.detailedCause())
                .preventionLesson(item.preventionLesson())
                .sourceUrl(item.sourceUrl())
                .status("PUBLISHED")
                .build();
    }

    /** KOSHA 날짜 문자열(yyyyMMdd) → Instant 변환. 파싱 실패 시 현재 시각 반환. */
    private Instant parseKoshaDate(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank()) return Instant.now();
        try {
            return LocalDate.parse(yyyyMMdd, KOSHA_DATE_FMT)
                    .atStartOfDay(ZoneId.of("Asia/Seoul"))
                    .toInstant();
        } catch (DateTimeParseException e) {
            log.debug("KOSHA 날짜 파싱 실패: '{}' → 현재 시각 사용", yyyyMMdd);
            return Instant.now();
        }
    }

    /**
     * 사고사례 텍스트 필드에서 키워드 추출 후 safety_incident_keyword 저장.
     * REQ-SAFETY-001-D-3: safety_keyword 사전 기반 토큰 매칭.
     */
    private void extractAndSaveKeywords(SafetyIncident incident) {
        List<String> tokens = collectTokens(incident);
        if (tokens.isEmpty()) return;
        List<SafetyKeyword> matched = keywordMapper.findMatchingKeywords(tokens);
        for (SafetyKeyword kw : matched) {
            SafetyIncidentKeyword mapping = SafetyIncidentKeyword.builder()
                    .incidentId(incident.getId())
                    .keywordId(kw.getId())
                    .weight(BigDecimal.ONE)
                    .build();
            incidentKeywordMapper.insert(mapping);
        }
        if (!matched.isEmpty()) {
            log.debug("키워드 추출 완료: incidentId={}, keywordCount={}", incident.getId(), matched.size());
        }
    }

    private List<String> collectTokens(SafetyIncident incident) {
        List<String> texts = new ArrayList<>();
        addTokens(texts, incident.getSummary());
        addTokens(texts, incident.getDetailedCause());
        addTokens(texts, incident.getPreventionLesson());
        addTokens(texts, incident.getIncidentType());
        addTokens(texts, incident.getLocation());
        return texts;
    }

    private void addTokens(List<String> out, String text) {
        if (text == null || text.isBlank()) return;
        String[] parts = text.split("[\\s,;。、·/\\-()\\[\\]]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private IncidentSummary toSummary(SafetyIncident i) {
        return new IncidentSummary(
                i.getId(), i.getSourceType(), i.getIndustryCode(),
                i.getIncidentType(), i.getSeverity(), i.getOccurredAt(),
                i.getCasualties(), i.getLocation(), i.getSummary(), i.getStatus()
        );
    }

    private IncidentDetail toDetail(SafetyIncident i) {
        return new IncidentDetail(
                i.getId(), i.getSourceType(), i.getIndustryCode(),
                i.getOccupationCode(), i.getProcessType(), i.getIncidentType(),
                i.getOccurredAt(), i.getSeverity(), i.getCasualties(), i.getLocation(),
                i.getSummary(), i.getDetailedCause(), i.getPreventionLesson(),
                i.getSourceUrl(), i.getStatus(), i.getCreatedAt(), i.getUpdatedAt()
        );
    }
}
