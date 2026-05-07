package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.search.dto.SynonymCreateRequest;
import kr.co.ircp.cms.domain.search.dto.SynonymUpdateRequest;
import kr.co.ircp.cms.domain.search.entity.SearchSynonym;
import kr.co.ircp.cms.domain.search.entity.SearchSynonymStatus;
import kr.co.ircp.cms.domain.search.exception.DuplicateSynonymException;
import kr.co.ircp.cms.domain.search.exception.SynonymNotFoundException;
import kr.co.ircp.cms.domain.search.exception.SynonymSelfException;
import kr.co.ircp.cms.domain.search.repository.SearchSynonymMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 동의어 사전 서비스 구현체.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: CRUD + expandQuery(OR 확장 + 20 토큰 절단).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 동의어 서비스 구현 (REQ-SEARCH-009)
// @MX:ANCHOR: [AUTO] SynonymServiceImpl — SearchServiceImpl, SynonymController가 호출 (fan_in >= 2)
// @MX:REASON: 동의어 CRUD + expandQuery는 검색 정확도/CTR에 직접 영향
// @MX:SPEC: SPEC-CMS-010#REQ-SEARCH-009
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SynonymServiceImpl implements SynonymService {

    /** 쿼리 확장 시 OR 토큰 최대 수 (RISK-S-05 폭주 방지) */
    private static final int MAX_EXPANSION_TOKENS = 20;

    private final SearchSynonymMapper synonymMapper;

    @Override
    public PageResponse<SearchSynonym> listSynonyms(String locale, int page, int size) {
        int offset = page * size;
        List<SearchSynonym> rows = synonymMapper.findAllActive(locale, offset, size);
        long total = synonymMapper.countAllActive(locale);
        return PageResponse.of(rows, page, size, total);
    }

    @Override
    @Transactional
    public SearchSynonym createSynonym(SynonymCreateRequest req, Long createdBy) {
        // term == synonym 자기참조 거부 (chk_ss_self CHECK 제약 사전 가드)
        if (req.term() != null && req.term().equals(req.synonym())) {
            throw new SynonymSelfException();
        }
        // UNIQUE(term, synonym, locale) 사전 검사 → 409 매핑
        if (synonymMapper.existsByTermAndSynonym(req.term(), req.synonym(), req.locale())) {
            throw new DuplicateSynonymException(req.term(), req.synonym(), req.locale());
        }
        SearchSynonym entity = SearchSynonym.builder()
                .term(req.term())
                .synonym(req.synonym())
                .locale(req.locale())
                .status(SearchSynonymStatus.ACTIVE.name())
                .description(req.description())
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();
        synonymMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public SearchSynonym updateSynonym(Long id, SynonymUpdateRequest req, Long updatedBy) {
        SearchSynonym existing = synonymMapper.findById(id)
                .orElseThrow(() -> new SynonymNotFoundException(id));
        synonymMapper.update(id, req.synonym(), req.status(), updatedBy);
        // 갱신 후 최신 본문 재조회
        return synonymMapper.findById(id).orElse(existing);
    }

    @Override
    @Transactional
    public void deleteSynonym(Long id, Long updatedBy) {
        if (synonymMapper.findById(id).isEmpty()) {
            throw new SynonymNotFoundException(id);
        }
        synonymMapper.softDelete(id, updatedBy);
    }

    @Override
    public String expandQuery(String query, String locale) {
        if (query == null || query.isBlank()) {
            return query;
        }
        // 공백 분리 토큰화 — 각 토큰에 대해 활성 동의어 추가
        String[] tokens = query.trim().split("\\s+");
        Set<String> expanded = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            expanded.add(token);
            List<SearchSynonym> synonyms = synonymMapper.findActiveByTerm(token, locale);
            for (SearchSynonym s : synonyms) {
                expanded.add(s.getSynonym());
                if (expanded.size() >= MAX_EXPANSION_TOKENS) {
                    break;
                }
            }
            if (expanded.size() >= MAX_EXPANSION_TOKENS) {
                break;
            }
        }
        // 토큰이 1개뿐이면(즉, 동의어 확장이 없었다면) 원본 그대로 반환
        if (expanded.size() <= tokens.length) {
            // 원본만 있고 확장이 발생하지 않음
            boolean hasSynonym = false;
            for (String tok : expanded) {
                boolean inOriginal = false;
                for (String orig : tokens) {
                    if (orig.equals(tok)) {
                        inOriginal = true;
                        break;
                    }
                }
                if (!inOriginal) {
                    hasSynonym = true;
                    break;
                }
            }
            if (!hasSynonym) {
                return query;
            }
        }
        // websearch_to_tsquery 친화 형식 — OR 결합
        // 절단 결과는 LinkedHashSet 순서를 따른다(원본 토큰 우선)
        List<String> trimmed = new ArrayList<>(expanded);
        if (trimmed.size() > MAX_EXPANSION_TOKENS) {
            trimmed = trimmed.subList(0, MAX_EXPANSION_TOKENS);
        }
        return String.join(" OR ", trimmed);
    }
}
