package kr.co.ircp.cms.domain.system.setting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingRequest;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingResponse;
import kr.co.ircp.cms.domain.system.setting.service.SystemSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SystemSettingController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-005 REQ-SYSTEM-005-D: 시스템 설정 조회/저장 HTTP 계층 검증.
 */
@WebMvcTest(SystemSettingController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SystemSettingController GREEN 테스트 (REQ-SYSTEM-005-D)")
class SystemSettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SystemSettingService settingService;

    private static SystemSettingResponse sample(String key, String value, String valueType) {
        return SystemSettingResponse.builder()
                .key(key)
                .value(value)
                .valueType(valueType)
                .description("설명")
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:SETTING:READ"})
    @DisplayName("GET /settings — 전체 목록 조회 200 OK")
    void list_returnsOkWithSettings() throws Exception {
        when(settingService.listAll()).thenReturn(List.of(
                sample("site.title", "iROUM", "STRING"),
                sample("site.maxUpload", "10", "INTEGER")
        ));

        mockMvc.perform(get("/api/v1/system/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].key").value("site.title"))
                .andExpect(jsonPath("$[1].valueType").value("INTEGER"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:SETTING:READ"})
    @DisplayName("GET /settings/{key} — 단건 조회 200 OK")
    void get_returnsOkWithSetting() throws Exception {
        when(settingService.get(eq("site.title"))).thenReturn(sample("site.title", "iROUM", "STRING"));

        mockMvc.perform(get("/api/v1/system/settings/site.title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("site.title"))
                .andExpect(jsonPath("$.value").value("iROUM"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:SETTING:WRITE"})
    @DisplayName("PUT /settings/{key} — UPSERT 200 OK + 응답 body")
    void put_returnsOkWithUpdatedSetting() throws Exception {
        SystemSettingRequest req = new SystemSettingRequest("iROUM CMS", "사이트 타이틀");
        when(settingService.put(eq("site.title"), any(SystemSettingRequest.class)))
                .thenReturn(sample("site.title", "iROUM CMS", "STRING"));

        mockMvc.perform(put("/api/v1/system/settings/site.title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("site.title"))
                .andExpect(jsonPath("$.value").value("iROUM CMS"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:SETTING:WRITE"})
    @DisplayName("PUT /settings/{key} — value 누락 시 400 Bad Request")
    void put_blankValue_returns400() throws Exception {
        // value가 @NotBlank 위반
        String invalidJson = "{\"value\":\"\",\"description\":\"빈 값\"}";

        mockMvc.perform(put("/api/v1/system/settings/site.title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /settings/{key} — 인증 없이 접근 시 403 Forbidden")
    void put_unauthenticated_returns403() throws Exception {
        SystemSettingRequest req = new SystemSettingRequest("iROUM CMS", "사이트 타이틀");

        mockMvc.perform(put("/api/v1/system/settings/site.title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
