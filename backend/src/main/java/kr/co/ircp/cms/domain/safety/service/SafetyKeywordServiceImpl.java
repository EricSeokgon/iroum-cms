package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.KeywordRequest;
import kr.co.ircp.cms.domain.safety.dto.KeywordSummary;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeyword;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeywordSynonym;
import kr.co.ircp.cms.domain.safety.exception.SafetyKeywordNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.SafetyKeywordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 안전 키워드 사전 서비스 구현.
 * REQ-SAFETY-002
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyKeywordServiceImpl implements SafetyKeywordService {

    private final SafetyKeywordMapper keywordMapper;

    @Override
    public List<KeywordSummary> listKeywords(String category) {
        List<SafetyKeyword> rows = keywordMapper.findByCategory(category);
        return rows.stream().map(this::toSummary).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public KeywordSummary createKeyword(KeywordRequest request) {
        SafetyKeyword keyword = SafetyKeyword.builder()
                .category(request.category())
                .code(request.code())
                .term(request.term())
                .description(request.description())
                .status("ACTIVE")
                .build();
        keywordMapper.insert(keyword);
        if (request.synonyms() != null) {
            for (String syn : request.synonyms()) {
                if (syn == null || syn.isBlank()) continue;
                keywordMapper.insertSynonym(SafetyKeywordSynonym.builder()
                        .keywordId(keyword.getId()).synonym(syn.trim()).build());
            }
        }
        return toSummary(keyword);
    }

    @Override
    @Transactional
    public KeywordSummary updateKeyword(Long id, KeywordRequest request) {
        SafetyKeyword existing = keywordMapper.findById(id)
                .orElseThrow(() -> new SafetyKeywordNotFoundException(id));
        existing.setCategory(request.category());
        existing.setTerm(request.term());
        existing.setDescription(request.description());
        keywordMapper.update(existing);
        if (request.synonyms() != null) {
            keywordMapper.deleteSynonymsByKeywordId(id);
            for (String syn : request.synonyms()) {
                if (syn == null || syn.isBlank()) continue;
                keywordMapper.insertSynonym(SafetyKeywordSynonym.builder()
                        .keywordId(id).synonym(syn.trim()).build());
            }
        }
        return toSummary(existing);
    }

    @Override
    @Transactional
    public void deactivateKeyword(Long id) {
        keywordMapper.findById(id).orElseThrow(() -> new SafetyKeywordNotFoundException(id));
        keywordMapper.deactivateById(id);
    }

    private KeywordSummary toSummary(SafetyKeyword k) {
        List<String> synonyms = keywordMapper.findSynonymsByKeywordId(k.getId())
                .stream().map(SafetyKeywordSynonym::getSynonym).toList();
        return new KeywordSummary(k.getId(), k.getCategory(), k.getCode(),
                k.getTerm(), k.getDescription(), k.getStatus(), synonyms);
    }
}
