package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.PublicationZipArchive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 발간자료 ZIP 아카이브 MyBatis 매퍼.
 * REQ-BOARD-012-D-4: ZIP 다운로드 요청 저장 + 만료 배치
 */
@Mapper
public interface PublicationZipArchiveMapper {

    /** ZIP 아카이브 신규 INSERT. */
    void insert(PublicationZipArchive archive);

    /** download_id로 단건 조회. */
    Optional<PublicationZipArchive> findByDownloadId(@Param("downloadId") UUID downloadId);

    /** 다운로드 카운트 1 증가 + last_downloaded_at 갱신. */
    void incrementDownloadCount(@Param("downloadId") UUID downloadId);

    /** 만료된 아카이브 조회 (deleted_at IS NULL AND expires_at < NOW()). */
    List<PublicationZipArchive> findExpired();

    /** 만료된 아카이브 소프트 삭제 (deleted_at = NOW()). */
    void softDeleteExpired();
}
