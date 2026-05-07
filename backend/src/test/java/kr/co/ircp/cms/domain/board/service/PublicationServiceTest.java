package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryDto;
import kr.co.ircp.cms.domain.board.dto.PublicationCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationDetail;
import kr.co.ircp.cms.domain.board.dto.PublicationSummary;
import kr.co.ircp.cms.domain.board.dto.PublicationUpdateRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadRequest;
import kr.co.ircp.cms.domain.board.dto.ZipDownloadResponse;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.entity.PublicationCategory;
import kr.co.ircp.cms.domain.board.entity.PublicationMeta;
import kr.co.ircp.cms.domain.board.entity.PublicationZipArchive;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PublicationNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.PublicationCategoryMapper;
import kr.co.ircp.cms.domain.board.repository.PublicationMetaMapper;
import kr.co.ircp.cms.domain.board.repository.PublicationZipArchiveMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PublicationService GREEN 단계 테스트.
 * REQ-BOARD-012: 발간자료 카테고리·메타데이터·다운로드 통계·ZIP 아카이브
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PublicationService GREEN 테스트 (REQ-BOARD-012)")
class PublicationServiceTest {

    @Mock
    private PublicationMetaMapper publicationMetaMapper;

    @Mock
    private PublicationCategoryMapper publicationCategoryMapper;

    @Mock
    private PublicationZipArchiveMapper publicationZipArchiveMapper;

    @Mock
    private BbsPostMapper bbsPostMapper;

    @Mock
    private BbsMasterMapper bbsMasterMapper;

    private PublicationService service;

    @BeforeEach
    void setUp() {
        service = new PublicationServiceImpl(
                publicationMetaMapper,
                publicationCategoryMapper,
                publicationZipArchiveMapper,
                bbsPostMapper,
                bbsMasterMapper
        );
    }

    // 공통 스텁 빌더 — 기본 상태의 PublicationMeta 생성
    private PublicationMeta stubMeta(long postId) {
        return PublicationMeta.builder()
                .postId(postId)
                .publicationYear((short) 2025)
                .publicationMonth((short) 6)
                .documentType("REPORT")
                .publicationCategoryId(10L)
                .fileCount(2)
                .isbn("978-89-1234-567-8")
                .publisher("이룸출판")
                .metadata("{}")
                .title("발간자료 " + postId)
                .contentHtml("<p>본문 " + postId + "</p>")
                .contentText("본문 " + postId)
                .viewCount(0L)
                .status("PUBLISHED")
                .publishedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .categoryName("정책연구")
                .build();
    }

    private BbsMaster stubBbsMaster() {
        return BbsMaster.builder()
                .id(99L)
                .code("PUBLICATION")
                .name("발간자료")
                .status("ACTIVE")
                .build();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-012-Q: 발간자료 목록 페이징 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("발간자료 목록 — page/size 기반 offset 계산 및 PageResponse 반환")
    void listPublications_returnsPageResponse() {
        // arrange — page=1, size=10이면 offset=10
        when(publicationMetaMapper.findWithFilters(
                eq(2025), eq(6), eq("REPORT"), eq(10L), eq("기후"), eq(10), eq(10)))
                .thenReturn(List.of(stubMeta(1L), stubMeta(2L)));
        when(publicationMetaMapper.countWithFilters(
                eq(2025), eq(6), eq("REPORT"), eq(10L), eq("기후")))
                .thenReturn(25L);

        // act
        PageResponse<PublicationSummary> result = service.listPublications(
                2025, 6, "REPORT", 10L, "기후", 1, 10);

        // assert
        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25L);
        assertThat(result.totalPages()).isEqualTo(3); // ceil(25/10) = 3
        verify(publicationMetaMapper).findWithFilters(
                2025, 6, "REPORT", 10L, "기후", 10, 10);
        verify(publicationMetaMapper).countWithFilters(
                2025, 6, "REPORT", 10L, "기후");
    }

    @Test
    @DisplayName("발간자료 목록 — 빈 결과면 totalElements=0, totalPages=0 반환")
    void listPublications_emptyResult_returnsEmptyPage() {
        // arrange
        when(publicationMetaMapper.findWithFilters(
                any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(List.of());
        when(publicationMetaMapper.countWithFilters(
                any(), any(), any(), any(), any()))
                .thenReturn(0L);

        // act
        PageResponse<PublicationSummary> result = service.listPublications(
                null, null, null, null, null, 0, 20);

        // assert
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-012-Q: 발간자료 단건 조회 + 조회수 증가
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("발간자료 단건 조회 — 존재하는 ID는 조회수 증가 후 상세 반환")
    void getPublication_existingId_incrementsViewCountAndReturnsDetail() {
        // arrange
        PublicationMeta meta = stubMeta(1L);
        when(publicationMetaMapper.findById(1L)).thenReturn(Optional.of(meta));

        // act
        PublicationDetail result = service.getPublication(1L);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.postId()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("발간자료 1");
        assertThat(result.publicationYear()).isEqualTo(2025);
        assertThat(result.publicationMonth()).isEqualTo(6);
        assertThat(result.documentType()).isEqualTo("REPORT");
        verify(publicationMetaMapper).incrementViewCount(1L);
    }

    @Test
    @DisplayName("발간자료 단건 조회 — 존재하지 않는 ID는 PublicationNotFoundException")
    void getPublication_nonExistentId_throwsPublicationNotFoundException() {
        // arrange
        when(publicationMetaMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getPublication(999L))
                .isInstanceOf(PublicationNotFoundException.class);

        verify(publicationMetaMapper, never()).incrementViewCount(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-012-C: 발간자료 신규 등록
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("발간자료 생성 — bbsMaster 조회 + bbsPost INSERT + meta INSERT + 재조회 반환")
    void createPublication_success_insertsPostAndMeta() {
        // arrange
        BbsMaster master = stubBbsMaster();
        when(bbsMasterMapper.findByCode("PUBLICATION")).thenReturn(Optional.of(master));

        // bbs_post insert 시 id를 부여하는 매퍼 동작 시뮬레이션
        ArgumentCaptor<BbsPost> postCaptor = ArgumentCaptor.forClass(BbsPost.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            BbsPost p = invocation.getArgument(0);
            p.setId(101L);
            return null;
        }).when(bbsPostMapper).insert(any(BbsPost.class));

        PublicationMeta saved = stubMeta(101L);
        when(publicationMetaMapper.findById(101L)).thenReturn(Optional.of(saved));

        PublicationCreateRequest req = new PublicationCreateRequest(
                "2025년 6월 보고서",
                "<p>본문 HTML</p>",
                "본문 텍스트",
                2025,
                6,
                "REPORT",
                10L,
                "978-89-1234-567-8",
                "이룸출판",
                "{}"
        );

        // act
        PublicationDetail result = service.createPublication(req, 100L);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.postId()).isEqualTo(101L);
        verify(bbsMasterMapper).findByCode("PUBLICATION");
        verify(bbsPostMapper).insert(postCaptor.capture());
        BbsPost inserted = postCaptor.getValue();
        assertThat(inserted.getBbsId()).isEqualTo(99L);
        assertThat(inserted.getTitle()).isEqualTo("2025년 6월 보고서");
        assertThat(inserted.getContentHtml()).isEqualTo("<p>본문 HTML</p>");
        assertThat(inserted.getContentText()).isEqualTo("본문 텍스트");
        assertThat(inserted.getAuthorId()).isEqualTo(100L);
        assertThat(inserted.getStatus()).isEqualTo("PUBLISHED");
        verify(publicationMetaMapper).insert(101L, req);
        verify(publicationMetaMapper).findById(101L);
    }

    @Test
    @DisplayName("발간자료 생성 — PUBLICATION 게시판 마스터 미존재 시 BbsMasterNotFoundException")
    void createPublication_publicationBbsMasterMissing_throwsBbsMasterNotFoundException() {
        // arrange
        when(bbsMasterMapper.findByCode("PUBLICATION")).thenReturn(Optional.empty());

        PublicationCreateRequest req = new PublicationCreateRequest(
                "제목", "<p>html</p>", null,
                2025, 6, "REPORT", null, null, null, null
        );

        // act + assert
        assertThatThrownBy(() -> service.createPublication(req, 100L))
                .isInstanceOf(BbsMasterNotFoundException.class);

        verify(bbsPostMapper, never()).insert(any());
        verify(publicationMetaMapper, never()).insert(any(), any());
    }

    @Test
    @DisplayName("발간자료 생성 — contentHtml=null이면 빈 문자열로 저장")
    void createPublication_nullContentHtml_defaultsToEmpty() {
        // arrange
        when(bbsMasterMapper.findByCode("PUBLICATION")).thenReturn(Optional.of(stubBbsMaster()));
        org.mockito.Mockito.doAnswer(invocation -> {
            BbsPost p = invocation.getArgument(0);
            p.setId(102L);
            return null;
        }).when(bbsPostMapper).insert(any(BbsPost.class));
        when(publicationMetaMapper.findById(102L)).thenReturn(Optional.of(stubMeta(102L)));

        PublicationCreateRequest req = new PublicationCreateRequest(
                "제목", null, null, // contentHtml 및 contentText 모두 null
                2025, 6, "REPORT", null, null, null, null
        );
        ArgumentCaptor<BbsPost> postCaptor = ArgumentCaptor.forClass(BbsPost.class);

        // act
        service.createPublication(req, 100L);

        // assert — null contentHtml은 빈 문자열로 변환
        verify(bbsPostMapper).insert(postCaptor.capture());
        BbsPost inserted = postCaptor.getValue();
        assertThat(inserted.getContentHtml()).isEqualTo("");
        // contentText도 stripHtml("") 결과로 "" 가 됨
        assertThat(inserted.getContentText()).isEqualTo("");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-012-U: 발간자료 부분 수정
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("발간자료 수정 — title이 변경되면 bbsPost.update 호출")
    void updatePublication_titleOnly_updatesPostAndMeta() {
        // arrange
        PublicationMeta existing = stubMeta(1L);
        PublicationMeta refreshed = stubMeta(1L);
        when(publicationMetaMapper.findById(1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(refreshed));

        PublicationUpdateRequest req = new PublicationUpdateRequest(
                "수정된 제목",
                null, // contentHtml
                null, // contentText
                null, null, null, null, null, null, null
        );

        // act
        PublicationDetail result = service.updatePublication(1L, req);

        // assert
        assertThat(result).isNotNull();
        verify(bbsPostMapper).update(any(BbsPost.class));
        verify(publicationMetaMapper).update(1L, req);
    }

    @Test
    @DisplayName("발간자료 수정 — title/contentHtml/contentText 모두 null이면 bbsPost.update 미호출")
    void updatePublication_metaOnly_skipsPostUpdate() {
        // arrange
        PublicationMeta existing = stubMeta(1L);
        PublicationMeta refreshed = stubMeta(1L);
        when(publicationMetaMapper.findById(1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(refreshed));

        // 메타 필드만 변경 (publicationYear, isbn 등)
        PublicationUpdateRequest req = new PublicationUpdateRequest(
                null, null, null,
                2024, 12, "BROCHURE", 20L, "978-89-9999-999-9", "신출판", null
        );

        // act
        service.updatePublication(1L, req);

        // assert — bbsPost.update는 호출되지 않고 meta만 업데이트
        verify(bbsPostMapper, never()).update(any());
        verify(publicationMetaMapper).update(1L, req);
    }

    @Test
    @DisplayName("발간자료 수정 — 존재하지 않는 ID는 PublicationNotFoundException")
    void updatePublication_nonExistentId_throwsPublicationNotFoundException() {
        // arrange
        when(publicationMetaMapper.findById(999L)).thenReturn(Optional.empty());

        PublicationUpdateRequest req = new PublicationUpdateRequest(
                "제목", null, null, null, null, null, null, null, null, null
        );

        // act + assert
        assertThatThrownBy(() -> service.updatePublication(999L, req))
                .isInstanceOf(PublicationNotFoundException.class);

        verify(bbsPostMapper, never()).update(any());
        verify(publicationMetaMapper, never()).update(any(), any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-012-D: 발간자료 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("발간자료 삭제 — 존재하는 ID는 bbsPost.deleteById 호출")
    void deletePublication_existingId_softDeletesBbsPost() {
        // arrange
        when(publicationMetaMapper.findById(1L)).thenReturn(Optional.of(stubMeta(1L)));
        when(bbsPostMapper.deleteById(1L)).thenReturn(1);

        // act
        service.deletePublication(1L);

        // assert
        verify(publicationMetaMapper).findById(1L);
        verify(bbsPostMapper).deleteById(1L);
    }

    @Test
    @DisplayName("발간자료 삭제 — meta 미존재 시 PublicationNotFoundException")
    void deletePublication_nonExistentMeta_throwsPublicationNotFoundException() {
        // arrange
        when(publicationMetaMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.deletePublication(999L))
                .isInstanceOf(PublicationNotFoundException.class);

        verify(bbsPostMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("발간자료 삭제 — bbsPost.deleteById가 0이면 PublicationNotFoundException")
    void deletePublication_postSoftDeleteAffectedZero_throwsPublicationNotFoundException() {
        // arrange — meta는 존재하지만 bbs_post 소프트 삭제가 0건 영향
        when(publicationMetaMapper.findById(1L)).thenReturn(Optional.of(stubMeta(1L)));
        when(bbsPostMapper.deleteById(1L)).thenReturn(0);

        // act + assert
        assertThatThrownBy(() -> service.deletePublication(1L))
                .isInstanceOf(PublicationNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-012-Q-1: 카테고리 트리 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("카테고리 트리 — flat 리스트로부터 parent_id 기준 트리 빌드")
    void getCategories_buildsTree_fromFlatList() {
        // arrange — 2 루트 + 3 하위 (root1 → child1 → grandchild, root2 → child2)
        PublicationCategory root1 = PublicationCategory.builder()
                .id(1L).code("POLICY").name("정책연구").parentId(null)
                .depth((short) 1).sortOrder(1).status("ACTIVE").build();
        PublicationCategory root2 = PublicationCategory.builder()
                .id(2L).code("STAT").name("통계자료").parentId(null)
                .depth((short) 1).sortOrder(2).status("ACTIVE").build();
        PublicationCategory child1 = PublicationCategory.builder()
                .id(3L).code("ECON").name("경제").parentId(1L)
                .depth((short) 2).sortOrder(1).status("ACTIVE").build();
        PublicationCategory grandchild = PublicationCategory.builder()
                .id(4L).code("MACRO").name("거시경제").parentId(3L)
                .depth((short) 3).sortOrder(1).status("ACTIVE").build();
        PublicationCategory child2 = PublicationCategory.builder()
                .id(5L).code("DEMO").name("인구통계").parentId(2L)
                .depth((short) 2).sortOrder(1).status("ACTIVE").build();

        when(publicationCategoryMapper.findAllActive())
                .thenReturn(List.of(root1, root2, child1, grandchild, child2));

        // act
        List<PublicationCategoryDto> result = service.getCategories();

        // assert — 루트 2개
        assertThat(result).hasSize(2);

        PublicationCategoryDto resultRoot1 = result.get(0);
        assertThat(resultRoot1.id()).isEqualTo(1L);
        assertThat(resultRoot1.code()).isEqualTo("POLICY");
        // root1 → child1 (id=3)
        assertThat(resultRoot1.children()).hasSize(1);
        PublicationCategoryDto resultChild1 = resultRoot1.children().get(0);
        assertThat(resultChild1.id()).isEqualTo(3L);
        // child1 → grandchild (id=4)
        assertThat(resultChild1.children()).hasSize(1);
        assertThat(resultChild1.children().get(0).id()).isEqualTo(4L);
        // grandchild는 자식 없음
        assertThat(resultChild1.children().get(0).children()).isEmpty();

        PublicationCategoryDto resultRoot2 = result.get(1);
        assertThat(resultRoot2.id()).isEqualTo(2L);
        // root2 → child2 (id=5)
        assertThat(resultRoot2.children()).hasSize(1);
        assertThat(resultRoot2.children().get(0).id()).isEqualTo(5L);
        assertThat(resultRoot2.children().get(0).children()).isEmpty();
    }

    @Test
    @DisplayName("카테고리 트리 — 빈 리스트면 빈 결과 반환")
    void getCategories_emptyList_returnsEmpty() {
        // arrange
        when(publicationCategoryMapper.findAllActive()).thenReturn(List.of());

        // act
        List<PublicationCategoryDto> result = service.getCategories();

        // assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("카테고리 트리 — 루트만 있고 자식 없으면 children=빈 리스트")
    void getCategories_singleRootNoChildren_returnsRootOnly() {
        // arrange
        PublicationCategory root = PublicationCategory.builder()
                .id(1L).code("ROOT").name("루트").parentId(null)
                .depth((short) 1).sortOrder(1).status("ACTIVE").build();
        when(publicationCategoryMapper.findAllActive()).thenReturn(List.of(root));

        // act
        List<PublicationCategoryDto> result = service.getCategories();

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).children()).isEmpty();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-012-D-4: ZIP 다운로드 SYNC/ASYNC 분기
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("ZIP 다운로드 — 5파일(50MB, ≤ 임계값) 동기 모드")
    void requestZipDownload_smallSize_returnsSync() {
        // arrange — 5 파일 × 10MB = 50MB (정확히 임계값, ≤ 50MB → SYNC)
        when(publicationMetaMapper.findById(1L)).thenReturn(Optional.of(stubMeta(1L)));
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            uuids.add(UUID.randomUUID());
        }
        ZipDownloadRequest req = new ZipDownloadRequest(uuids);

        // act
        ZipDownloadResponse result = service.requestZipDownload(1L, req, 100L);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.mode()).isEqualTo("SYNC");
        assertThat(result.message()).contains("동기");
        assertThat(result.sizeBytes()).isEqualTo(50L * 1024 * 1024);
        assertThat(result.downloadId()).isNotNull();
    }

    @Test
    @DisplayName("ZIP 다운로드 — 6파일(60MB, > 임계값) 비동기 모드")
    void requestZipDownload_oversizeSize_returnsAsync() {
        // arrange — 6 파일 × 10MB = 60MB (> 50MB → ASYNC)
        when(publicationMetaMapper.findById(1L)).thenReturn(Optional.of(stubMeta(1L)));
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            uuids.add(UUID.randomUUID());
        }
        ZipDownloadRequest req = new ZipDownloadRequest(uuids);

        // act
        ZipDownloadResponse result = service.requestZipDownload(1L, req, 100L);

        // assert
        assertThat(result.mode()).isEqualTo("ASYNC");
        assertThat(result.message()).contains("비동기");
        assertThat(result.sizeBytes()).isEqualTo(60L * 1024 * 1024);
    }

    @Test
    @DisplayName("ZIP 다운로드 — 0파일이면 0 bytes ≤ 임계값 → SYNC")
    void requestZipDownload_zeroFiles_returnsSync() {
        // arrange — 빈 자산 목록 → 0 bytes ≤ 50MB → SYNC
        when(publicationMetaMapper.findById(1L)).thenReturn(Optional.of(stubMeta(1L)));
        ZipDownloadRequest req = new ZipDownloadRequest(Collections.emptyList());

        // act
        ZipDownloadResponse result = service.requestZipDownload(1L, req, 100L);

        // assert
        assertThat(result.mode()).isEqualTo("SYNC");
        assertThat(result.sizeBytes()).isZero();
    }

    @Test
    @DisplayName("ZIP 다운로드 — meta 미존재 시 PublicationNotFoundException")
    void requestZipDownload_nonExistentMeta_throwsPublicationNotFoundException() {
        // arrange
        when(publicationMetaMapper.findById(999L)).thenReturn(Optional.empty());
        ZipDownloadRequest req = new ZipDownloadRequest(List.of(UUID.randomUUID()));

        // act + assert
        assertThatThrownBy(() -> service.requestZipDownload(999L, req, 100L))
                .isInstanceOf(PublicationNotFoundException.class);

        verify(publicationZipArchiveMapper, never()).insert(any());
    }

    @Test
    @DisplayName("ZIP 다운로드 — 아카이브 INSERT 시 downloadId/postId/requestedBy/mode 정확히 전달")
    void requestZipDownload_insertsArchive_withGeneratedDownloadIdAndMetadata() {
        // arrange
        when(publicationMetaMapper.findById(1L)).thenReturn(Optional.of(stubMeta(1L)));
        List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
        ZipDownloadRequest req = new ZipDownloadRequest(uuids);
        ArgumentCaptor<PublicationZipArchive> captor = ArgumentCaptor.forClass(PublicationZipArchive.class);

        // act
        ZipDownloadResponse result = service.requestZipDownload(1L, req, 100L);

        // assert
        verify(publicationZipArchiveMapper).insert(captor.capture());
        PublicationZipArchive archive = captor.getValue();
        assertThat(archive.getDownloadId()).isNotNull();
        assertThat(archive.getDownloadId()).isEqualTo(result.downloadId());
        assertThat(archive.getPostId()).isEqualTo(1L);
        assertThat(archive.getRequestedBy()).isEqualTo(100L);
        assertThat(archive.getMode()).isEqualTo("SYNC"); // 2 × 10MB = 20MB ≤ 50MB
        assertThat(archive.getSizeBytes()).isEqualTo(20L * 1024 * 1024);
        assertThat(archive.getAssetUuids()).isEqualTo(uuids);
        assertThat(archive.getZipFilePath()).contains(archive.getDownloadId().toString());
    }
}
