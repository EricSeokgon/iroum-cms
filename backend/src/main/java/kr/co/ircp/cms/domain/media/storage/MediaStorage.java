package kr.co.ircp.cms.domain.media.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 미디어 저장소 추상화 인터페이스.
 * Q-2 결정: 1차는 LocalFileSystemStorage 단일 구현.
 * v0.2+ MinIO/S3 어댑터 추가 시 이 인터페이스를 구현하면 됨.
 */
public interface MediaStorage {

    /**
     * 파일을 저장하고 저장 경로(stored_path)를 반환한다.
     * 경로 규칙: ${basePath}/{yyyy}/{MM}/{uuid}_{sanitizedFilename}
     */
    String store(MultipartFile file, String subPath) throws IOException;

    /**
     * 썸네일 바이트 배열을 저장하고 저장 경로를 반환한다.
     */
    String storeBytes(byte[] data, String relativePath) throws IOException;

    /** 저장된 파일을 물리적으로 삭제한다 (소프트 삭제 후 정리용). */
    void delete(String storedPath) throws IOException;

    /** storedPath → 절대 Path 변환 */
    Path toAbsolutePath(String storedPath);
}
