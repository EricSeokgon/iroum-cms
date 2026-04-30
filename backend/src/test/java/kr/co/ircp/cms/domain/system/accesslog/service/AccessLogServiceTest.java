package kr.co.ircp.cms.domain.system.accesslog.service;

import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogResponse;
import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogSearchRequest;
import kr.co.ircp.cms.domain.system.accesslog.entity.AccessLog;
import kr.co.ircp.cms.domain.system.accesslog.mapper.AccessLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AccessLogService GREEN 테스트.
 * REQ-SYSTEM-001-D: 비동기 저장, 검색·페이징
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessLogService GREEN 테스트 (REQ-SYSTEM-001-D)")
class AccessLogServiceTest {

    @Mock private AccessLogMapper accessLogMapper;

    private AccessLogServiceImpl accessLogService;

    @BeforeEach
    void setUp() {
        accessLogService = new AccessLogServiceImpl(accessLogMapper);
    }

    @Test
    @DisplayName("record() — mapper.insert 호출")
    void record_calls_mapper_insert() {
        // given
        AccessLog log = AccessLog.builder()
                .siteId(1L)
                .ipHash("abc123")
                .pageUrl("/api/v1/test")
                .statusCode(200)
                .responseTimeMs(42)
                .createdAt(Instant.now())
                .build();

        // when
        accessLogService.record(log);

        // then
        verify(accessLogMapper).insert(log);
    }

    @Test
    @DisplayName("record() — mapper 예외 발생 시 예외 전파 없이 흡수")
    void record_absorbs_exception() {
        // given
        AccessLog log = AccessLog.builder()
                .siteId(1L).ipHash("x").pageUrl("/err").statusCode(500).responseTimeMs(1)
                .build();
        doThrow(new RuntimeException("DB down")).when(accessLogMapper).insert(any());

        // when / then — 예외가 전파되지 않아야 함
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> accessLogService.record(log));
    }

    @Test
    @DisplayName("search() — 검색 결과 AccessLogResponse 변환")
    void search_returns_response_list() {
        // given
        AccessLog entity = AccessLog.builder()
                .id(1L).siteId(1L).ipHash("hash1").pageUrl("/home")
                .statusCode(200).responseTimeMs(30).createdAt(Instant.now())
                .build();
        AccessLogSearchRequest req = new AccessLogSearchRequest(null, null, null, null, 0, 20);
        when(accessLogMapper.findBySearch(req)).thenReturn(List.of(entity));

        // when
        List<AccessLogResponse> result = accessLogService.search(req);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).pageUrl()).isEqualTo("/home");
    }

    @Test
    @DisplayName("count() — 총 건수 반환")
    void count_returns_total() {
        // given
        AccessLogSearchRequest req = new AccessLogSearchRequest(null, null, 200, null, 0, 20);
        when(accessLogMapper.countBySearch(req)).thenReturn(42L);

        // when
        long count = accessLogService.count(req);

        // then
        assertThat(count).isEqualTo(42L);
    }
}
