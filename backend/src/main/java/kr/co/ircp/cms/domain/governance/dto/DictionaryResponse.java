package kr.co.ircp.cms.domain.governance.dto;

import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.entity.DataDictionaryHistory;

import java.time.Instant;
import java.util.List;

/**
 * data_dictionary 응답 DTO.
 *
 * <p>history는 단건 조회 시에만 포함되고 목록 조회 시에는 null.
 */
public record DictionaryResponse(
        Long id,
        String tableName,
        String columnName,
        String logicalNameKo,
        String logicalNameEn,
        String dataDomain,
        String dataType,
        String description,
        Boolean isPii,
        Boolean isRequired,
        String status,
        Instant createdAt,
        Instant updatedAt,
        List<HistoryEntry> history
) {

    public static DictionaryResponse from(DataDictionary d) {
        return from(d, null);
    }

    public static DictionaryResponse from(DataDictionary d, List<DataDictionaryHistory> hist) {
        List<HistoryEntry> entries = hist == null
                ? null
                : hist.stream().map(HistoryEntry::from).toList();
        return new DictionaryResponse(
                d.getId(), d.getTableName(), d.getColumnName(),
                d.getLogicalNameKo(), d.getLogicalNameEn(),
                d.getDataDomain(), d.getDataType(), d.getDescription(),
                d.getIsPii(), d.getIsRequired(), d.getStatus(),
                d.getCreatedAt(), d.getUpdatedAt(), entries);
    }

    public record HistoryEntry(
            Long id,
            String fieldChanged,
            String oldValue,
            String newValue,
            Long changedBy,
            Instant changedAt
    ) {
        public static HistoryEntry from(DataDictionaryHistory h) {
            return new HistoryEntry(
                    h.getId(), h.getFieldChanged(),
                    h.getOldValue(), h.getNewValue(),
                    h.getChangedBy(), h.getChangedAt());
        }
    }
}
