package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.BbsPostI18n;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 다국어 번역 MyBatis 매퍼.
 * SPEC-CMS-NOTICE-I18N-001: bbs_post_i18n 테이블 CRUD.
 *
 * // @MX:ANCHOR: [AUTO] BbsPostI18nMapper — 게시글 번역 데이터 접근 계층
 * // @MX:REASON: PostServiceImpl 번역 4개 메서드에서 참조 (fan_in >= 3)
 * // @MX:SPEC: SPEC-CMS-NOTICE-I18N-001
 */
@Mapper
public interface BbsPostI18nMapper {

    /** postId + language로 단건 번역 조회 */
    Optional<BbsPostI18n> findByPostIdAndLang(
            @Param("postId") Long postId,
            @Param("language") String language
    );

    /** postId의 전체 번역 목록 조회 */
    List<BbsPostI18n> findByPostId(@Param("postId") Long postId);

    /** 번역 upsert (INSERT ... ON CONFLICT DO UPDATE) */
    void upsert(BbsPostI18n i18n);

    /** postId + language 번역 삭제 */
    void deleteByPostIdAndLang(
            @Param("postId") Long postId,
            @Param("language") String language
    );
}
