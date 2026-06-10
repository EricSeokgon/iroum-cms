package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.PublicationCategoryCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryDto;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryUpdateRequest;

import java.util.List;

/**
 * 발간자료 카테고리 관리자 서비스 인터페이스.
 * SPEC-CMS-PUB-CAT-001 REQ-PCA-001~004
 */
public interface PublicationCategoryAdminService {

    /** REQ-PCA-004: 어드민용 전체 카테고리 트리 조회 (INACTIVE 포함). */
    List<PublicationCategoryDto> listAllForAdmin();

    /** REQ-PCA-001: 카테고리 생성. */
    PublicationCategoryDto createCategory(PublicationCategoryCreateRequest request);

    /** REQ-PCA-002: 카테고리 수정. */
    PublicationCategoryDto updateCategory(Long id, PublicationCategoryUpdateRequest request);

    /** REQ-PCA-003: 카테고리 삭제 (자식/연결 발간자료 존재 시 409). */
    void deleteCategory(Long id);
}
