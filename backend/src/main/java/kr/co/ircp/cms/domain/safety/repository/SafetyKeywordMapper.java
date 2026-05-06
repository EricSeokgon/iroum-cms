package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.entity.SafetyKeyword;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeywordSynonym;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 안전 키워드 사전 MyBatis 매퍼.
 * REQ-SAFETY-002
 */
@Mapper
public interface SafetyKeywordMapper {

    /** 카테고리별 active 키워드. category null이면 전체. */
    List<SafetyKeyword> findByCategory(@Param("category") String category);

    Optional<SafetyKeyword> findById(@Param("id") Long id);

    Optional<SafetyKeyword> findByCode(@Param("code") String code);

    void insert(SafetyKeyword keyword);

    int update(SafetyKeyword keyword);

    int deactivateById(@Param("id") Long id);

    // ─── 동의어 ─────────────────────────────────────────────────────────────
    List<SafetyKeywordSynonym> findSynonymsByKeywordId(@Param("keywordId") Long keywordId);

    void insertSynonym(SafetyKeywordSynonym synonym);

    int deleteSynonymsByKeywordId(@Param("keywordId") Long keywordId);

    /**
     * 프로필 텍스트 (industry_code, primary_process, hazard_factors 등)에서
     * 등장하는 키워드를 카테고리·가중치별로 매칭. 1차 keyword 정확 일치 + 동의어.
     */
    List<SafetyKeyword> findMatchingKeywords(
            @Param("texts") List<String> texts
    );
}
