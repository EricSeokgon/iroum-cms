package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.entity.DataDictionaryHistory;
import kr.co.ircp.cms.domain.governance.repository.DataDictionaryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-009 REQ-GOV-003: 변경 이력 자동 적재 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class DataDictionaryServiceTest {

    @Mock
    private DataDictionaryMapper mapper;

    @InjectMocks
    private DataDictionaryService service;

    @Test
    void update_recordsHistory_forEachChangedField() {
        DataDictionary before = DataDictionary.builder()
                .id(10L)
                .tableName("users")
                .columnName("email")
                .logicalNameKo("이메일")
                .logicalNameEn("Email")
                .dataDomain("MASTER")
                .dataType("VARCHAR(100)")
                .description("기존 설명")
                .status("ACTIVE")
                .build();

        DataDictionary updated = DataDictionary.builder()
                .id(10L)
                .tableName("users")
                .columnName("email")
                .logicalNameKo("전자우편")           // 변경
                .logicalNameEn("Email")
                .dataDomain("MASTER")
                .dataType("VARCHAR(200)")            // 변경
                .description("기존 설명")
                .status("ACTIVE")
                .build();

        when(mapper.findById(10L)).thenReturn(Optional.of(before));

        service.update(updated, 99L);

        ArgumentCaptor<DataDictionaryHistory> historyCaptor = ArgumentCaptor.forClass(DataDictionaryHistory.class);
        verify(mapper, times(2)).insertHistory(historyCaptor.capture());
        verify(mapper).update(any(DataDictionary.class));

        var captured = historyCaptor.getAllValues();
        assertThat(captured).extracting(DataDictionaryHistory::getFieldChanged)
                .containsExactlyInAnyOrder("logical_name_ko", "data_type");
        assertThat(captured).allSatisfy(h ->
                assertThat(h.getChangedBy()).isEqualTo(99L));
    }

    @Test
    void update_noHistoryRecorded_whenNothingChanged() {
        DataDictionary same = DataDictionary.builder()
                .id(10L)
                .tableName("users")
                .columnName("email")
                .logicalNameKo("이메일")
                .dataDomain("MASTER")
                .dataType("VARCHAR(100)")
                .status("ACTIVE")
                .build();

        when(mapper.findById(10L)).thenReturn(Optional.of(same));

        service.update(same, 99L);

        verify(mapper, times(0)).insertHistory(any());
        verify(mapper).update(any(DataDictionary.class));
    }
}
