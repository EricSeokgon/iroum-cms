package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.common.dto.RevisionDiffResponse;
import kr.co.ircp.cms.common.util.LineDiffCalculator;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryItem;
import kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 게시글 버전 히스토리 read 전용 서비스 구현체.
 * SPEC-CMS-POST-HISTORY-001 REQ-PH-001~005
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostHistoryServiceImpl implements PostHistoryService {

    private final BbsPostHistoryMapper bbsPostHistoryMapper;
    private final LineDiffCalculator lineDiffCalculator;
    private final kr.co.ircp.cms.domain.board.repository.BbsPostMapper bbsPostMapper;

    @Override
    public PageResponse<PostHistoryItem> getHistory(Long postId, int page, int size) {
        int offset = page * size;
        List<PostHistoryItem> content = bbsPostHistoryMapper.findPageByPostId(postId, offset, size);
        long total = bbsPostHistoryMapper.countByPostId(postId);
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public PostHistoryDetail getVersion(Long postId, int version) {
        return bbsPostHistoryMapper.findByPostIdAndVersion(postId, version)
                .orElseThrow(() -> new PostHistoryVersionNotFoundException(postId, version));
    }

    @Override
    public List<RevisionDiffResponse> diff(Long postId, int fromVersion, int toVersion) {
        // getVersion이 (postId, version) 부재 시 PostHistoryVersionNotFoundException(404)을 던진다.
        PostHistoryDetail from = getVersion(postId, fromVersion);
        PostHistoryDetail to = getVersion(postId, toVersion);
        return List.of(
                diffField("title", fromVersion, toVersion, from.title(), to.title()),
                diffField("content", fromVersion, toVersion, from.contentHtml(), to.contentHtml())
        );
    }

    // @MX:ANCHOR: [AUTO] rollback — 게시물 특정 버전 복원의 단일 진입점(이력 보존 + 낙관적 잠금)
    // @MX:REASON: PostController.rollbackPostVersion 및 향후 운영 도구에서 재사용되는 쓰기 경로(공개 API 경계).
    //             롤백도 하나의 리비전이라는 불변식(version+1, 직전 상태 이력 보존)을 보장.
    // @MX:SPEC: SPEC-CMS-CONTENT-REVISION-001 REQ-REV-007
    @Override
    @Transactional
    public PostHistoryDetail rollback(Long postId, int rollbackToVersion, int expectedVersion) {
        kr.co.ircp.cms.domain.board.entity.BbsPost post = bbsPostMapper.findById(postId)
                .orElseThrow(() -> new kr.co.ircp.cms.domain.board.exception.PostNotFoundException(postId));

        // 복원 대상 버전 스냅샷 (없으면 404)
        PostHistoryDetail snapshot = bbsPostHistoryMapper.findByPostIdAndVersion(postId, rollbackToVersion)
                .orElseThrow(() -> new PostHistoryVersionNotFoundException(postId, rollbackToVersion));

        // 낙관적 잠금: 게시물 현재 version 과 expectedVersion 불일치 시 409
        if (post.getVersion() != expectedVersion) {
            throw new kr.co.ircp.cms.common.exception.RevisionConflictException(post.getVersion());
        }

        // 롤백 직전(현재) 상태를 이력에 보존 — updatePost 관례와 동일하게 변경 전 본문을 누적한다.
        int nextVersion = bbsPostHistoryMapper.nextVersionByPostId(postId);
        kr.co.ircp.cms.domain.board.entity.BbsPostHistory preRollback =
                kr.co.ircp.cms.domain.board.entity.BbsPostHistory.builder()
                        .postId(postId)
                        .version(nextVersion)
                        .title(post.getTitle())
                        .contentHtml(post.getContentHtml())
                        .editReason("ROLLBACK_FROM_v" + rollbackToVersion)
                        .build();
        bbsPostHistoryMapper.insert(preRollback);

        // 게시물 복원: title + content_html (content_text 는 본 SPEC 범위 밖 — 복원 대상 아님).
        // WHERE version = expectedVersion 충돌 검출을 위해 expectedVersion 주입(SQL 이 version+1).
        post.setTitle(snapshot.title());
        post.setContentHtml(snapshot.contentHtml());
        post.setVersion(expectedVersion);

        int updatedRows = bbsPostMapper.update(post);
        if (updatedRows == 0) {
            // 동시성으로 인한 버전 불일치 → 서버 현재 버전을 실어 409
            long currentVersion = bbsPostMapper.findById(postId)
                    .map(kr.co.ircp.cms.domain.board.entity.BbsPost::getVersion)
                    .map(Integer::longValue)
                    .orElse((long) expectedVersion);
            throw new kr.co.ircp.cms.common.exception.RevisionConflictException(currentVersion);
        }

        // 롤백 후 상태 표현: version = 직전 version + 1, title/contentHtml = 복원 값
        return new PostHistoryDetail(
                post.getId(), expectedVersion + 1, null,
                "ROLLBACK_FROM_v" + rollbackToVersion, java.time.Instant.now(),
                snapshot.title(), snapshot.contentHtml());
    }

    /** 단일 필드의 라인 diff를 RevisionDiffResponse로 감싼다. */
    private RevisionDiffResponse diffField(String field, int fromVersion, int toVersion,
                                           String fromText, String toText) {
        return new RevisionDiffResponse(
                field, fromVersion, toVersion, lineDiffCalculator.calculate(fromText, toText));
    }
}
