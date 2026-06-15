package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 게시글 엔티티.
 * REQ-BOARD-002-D: 게시글 CRUD + 페이징·검색
 */
// @MX:NOTE: @NoArgsConstructor + @AllArgsConstructor 필수 — MyBatis DefaultObjectFactory가 리플렉션으로 no-args 생성자 호출.
// @Builder 단독 사용 시 package-private all-args 생성자만 생성되어 MyBatisSystemException 발생.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BbsPost {

    private Long id;
    private Long bbsId;
    private String title;
    private String contentHtml;
    private String contentText;
    private String categoryCode;
    private Long authorId;
    private String authorName;
    // @MX:NOTE: Lombok @Data + boolean 필드명 규칙: "isXxx" 대신 "xxx"로 선언해야 MyBatis resultMap property 매핑이 정상 작동
    // Lombok은 `notice` 필드에서 getter isNotice(), setter setNotice()를 생성하므로 MyBatis property="notice"로 매핑됨
    private boolean notice;
    private Instant noticeFrom;
    private Instant noticeUntil;
    private boolean secret;
    private long viewCount;
    private long likeCount;
    private int commentCount;
    private int attachmentCount;
    private String status;
    private Instant publishedAt;
    // SPEC-CMS-POST-SCHEDULE-001: 예약 발행 시각 (NULL=예약 없음, status=SCHEDULED 일 때만 의미)
    private Instant scheduledAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    // SPEC-CMS-NOTICE-I18N-002: LEFT JOIN bbs_post_i18n 결과에서 채워지는 필드. DB 컬럼 없음.
    private String language;

    // SPEC-CMS-AI-004: AI 스마트 태그 (V54 bbs_post.tags TEXT[] NOT NULL DEFAULT '{}').
    // @Builder.Default 로 null 방지 — StringArrayTypeHandler.createArrayOf 는 null 입력 시 NPE 발생.
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
