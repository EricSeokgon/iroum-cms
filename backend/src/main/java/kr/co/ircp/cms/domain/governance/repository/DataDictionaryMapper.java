package kr.co.ircp.cms.domain.governance.repository;

import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.entity.DataDictionaryHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 데이터 표준 사전 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-009 REQ-GOV-001~005.
 */
@Mapper
public interface DataDictionaryMapper {

    Optional<DataDictionary> findById(@Param("id") Long id);

    Optional<DataDictionary> findByTableAndColumn(@Param("tableName") String tableName,
                                                    @Param("columnName") String columnName);

    List<DataDictionary> findAll();

    /** 필터 + 페이징 조회. params: tableName, domain, status, offset, size */
    List<DataDictionary> findFiltered(@Param("p") Map<String, Object> params);

    int countFiltered(@Param("p") Map<String, Object> params);

    /** Export 전용: ACTIVE 상태 전체 (페이징 없음). */
    List<DataDictionary> findAllForExport();

    List<DataDictionary> findByTable(@Param("tableName") String tableName);

    void insert(DataDictionary dictionary);

    void update(DataDictionary dictionary);

    /** 소프트 삭제 — status='REMOVED'. */
    int softDelete(@Param("id") Long id);

    void insertHistory(DataDictionaryHistory history);

    List<DataDictionaryHistory> findHistory(@Param("dictionaryId") Long dictionaryId);

    /**
     * 실제 schema 컬럼 목록 조회 (information_schema).
     * DictionaryFreshnessJob에서 사용.
     */
    List<SchemaColumn> findActualSchemaColumns();

    /**
     * 실제 schema 컬럼 정보.
     */
    class SchemaColumn {
        private String tableName;
        private String columnName;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
    }
}
