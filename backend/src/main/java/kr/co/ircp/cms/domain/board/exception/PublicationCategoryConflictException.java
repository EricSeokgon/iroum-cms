package kr.co.ircp.cms.domain.board.exception;

/**
 * 발간자료 카테고리 삭제 시 자식 카테고리 또는 연결된 발간자료가 존재하는 경우 발생.
 * REQ-PCA-003: 삭제 불가 조건 → HTTP 409 Conflict
 */
public class PublicationCategoryConflictException extends RuntimeException {

    public PublicationCategoryConflictException(String message) {
        super(message);
    }
}
