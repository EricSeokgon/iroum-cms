package kr.co.ircp.cms.domain.media.storage;

import kr.co.ircp.cms.domain.media.config.MediaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 로컬 파일 시스템 기반 미디어 저장소.
 * Q-2 결정: 1차는 LocalFileSystemStorage 단일 구현 (2026-04-29).
 * MinIO/S3는 v0.2+ 후속 검토 — MediaStorage 인터페이스로 교체 가능.
 *
 * // @MX:NOTE: [AUTO] 저장 경로 = basePath + subPath. webroot 외부 디렉터리여야 직접 접근 차단됨.
 */
@Component
@RequiredArgsConstructor
public class LocalFileSystemStorage implements MediaStorage {

    private final MediaProperties properties;

    @Override
    public String store(MultipartFile file, String subPath) throws IOException {
        Path targetPath = Paths.get(properties.getBasePath()).resolve(subPath);
        Files.createDirectories(targetPath.getParent());
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toString();
    }

    @Override
    public String storeBytes(byte[] data, String relativePath) throws IOException {
        Path targetPath = Paths.get(properties.getBasePath()).resolve(relativePath);
        Files.createDirectories(targetPath.getParent());
        Files.write(targetPath, data);
        return targetPath.toString();
    }

    @Override
    public void delete(String storedPath) throws IOException {
        Path path = Paths.get(storedPath);
        Files.deleteIfExists(path);
    }

    @Override
    public Path toAbsolutePath(String storedPath) {
        return Paths.get(storedPath);
    }
}
