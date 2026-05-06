package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.entity.DataDictionaryHistory;
import kr.co.ircp.cms.domain.governance.repository.DataDictionaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 데이터 표준 사전 서비스.
 *
 * <p>SPEC-CMS-009 REQ-GOV-001~005: CRUD + 변경 이력 + Export.
 */
// @MX:NOTE: [AUTO] DataDictionaryService — DictionaryController + DictionaryFreshnessJob 진입점
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataDictionaryService {

    private final DataDictionaryMapper mapper;

    public Optional<DataDictionary> findById(Long id) {
        return mapper.findById(id);
    }

    public Optional<DataDictionary> findByTableAndColumn(String tableName, String columnName) {
        return mapper.findByTableAndColumn(tableName, columnName);
    }

    public List<DataDictionary> findAll() {
        return mapper.findAll();
    }

    public List<DataDictionary> findByTable(String tableName) {
        return mapper.findByTable(tableName);
    }

    public List<DataDictionaryHistory> findHistory(Long dictionaryId) {
        return mapper.findHistory(dictionaryId);
    }

    public PageResponse<DataDictionary> findFiltered(String tableName,
                                                      String domain,
                                                      String status,
                                                      int page,
                                                      int size) {
        Map<String, Object> p = new HashMap<>();
        p.put("tableName", tableName);
        p.put("domain", domain);
        p.put("status", status);
        p.put("offset", page * size);
        p.put("size", size);

        List<DataDictionary> content = mapper.findFiltered(p);
        long total = mapper.countFiltered(p);
        return PageResponse.of(content, page, size, total);
    }

    @Transactional
    public DataDictionary create(DataDictionary dictionary) {
        mapper.insert(dictionary);
        return dictionary;
    }

    /**
     * 사전 항목 수정 + 변경 이력 자동 적재 (REQ-GOV-003).
     * 변경된 컬럼 별로 history row를 INSERT한다.
     */
    @Transactional
    public DataDictionary update(DataDictionary updated, Long changedBy) {
        DataDictionary before = mapper.findById(updated.getId())
                .orElseThrow(() -> new IllegalArgumentException("data_dictionary not found: id=" + updated.getId()));

        recordHistory(before, updated, "logical_name_ko", before.getLogicalNameKo(), updated.getLogicalNameKo(), changedBy);
        recordHistory(before, updated, "logical_name_en", before.getLogicalNameEn(), updated.getLogicalNameEn(), changedBy);
        recordHistory(before, updated, "data_domain",     before.getDataDomain(),    updated.getDataDomain(),    changedBy);
        recordHistory(before, updated, "data_type",       before.getDataType(),      updated.getDataType(),      changedBy);
        recordHistory(before, updated, "description",     before.getDescription(),   updated.getDescription(),   changedBy);
        recordHistory(before, updated, "status",          before.getStatus(),        updated.getStatus(),        changedBy);

        mapper.update(updated);
        return updated;
    }

    /** 소프트 삭제 — status='REMOVED'. */
    @Transactional
    public boolean softDelete(Long id) {
        return mapper.softDelete(id) > 0;
    }

    /**
     * Export — CSV 또는 XLSX 바이트 배열 반환.
     *
     * <p>REQ-GOV-005 — 데이터 표준 사전 다운로드.
     */
    // @MX:NOTE: [AUTO] SXSSFWorkbook 스트리밍 export — 100k 레코드 OOM 없음
    public byte[] exportDictionary(String format) {
        List<DataDictionary> rows = mapper.findAllForExport();
        return "xlsx".equalsIgnoreCase(format) ? toXlsx(rows) : toCsv(rows);
    }

    private byte[] toCsv(List<DataDictionary> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("﻿"); // UTF-8 BOM (Excel 호환)
        sb.append("테이블명,컬럼명,한글명,영문명,도메인,데이터타입,설명,개인정보여부\n");
        for (DataDictionary d : rows) {
            sb.append(csv(d.getTableName())).append(",")
              .append(csv(d.getColumnName())).append(",")
              .append(csv(d.getLogicalNameKo())).append(",")
              .append(csv(d.getLogicalNameEn())).append(",")
              .append(csv(d.getDataDomain())).append(",")
              .append(csv(d.getDataType())).append(",")
              .append(csv(d.getDescription())).append(",")
              .append(Boolean.TRUE.equals(d.getIsPii()) ? "Y" : "N").append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] toXlsx(List<DataDictionary> rows) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = wb.createSheet("data_dictionary");
            String[] headers = {"테이블명", "컬럼명", "한글명", "영문명",
                    "도메인", "데이터타입", "설명", "개인정보여부"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }
            int rowIdx = 1;
            for (DataDictionary d : rows) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(nz(d.getTableName()));
                r.createCell(1).setCellValue(nz(d.getColumnName()));
                r.createCell(2).setCellValue(nz(d.getLogicalNameKo()));
                r.createCell(3).setCellValue(nz(d.getLogicalNameEn()));
                r.createCell(4).setCellValue(nz(d.getDataDomain()));
                r.createCell(5).setCellValue(nz(d.getDataType()));
                r.createCell(6).setCellValue(nz(d.getDescription()));
                r.createCell(7).setCellValue(Boolean.TRUE.equals(d.getIsPii()) ? "Y" : "N");
            }
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            log.error("XLSX export failed", e);
            throw new IllegalStateException("XLSX export failed: " + e.getMessage(), e);
        }
    }

    private static String csv(String v) {
        if (v == null) return "";
        // Excel 호환: 콤마/따옴표/줄바꿈 포함시 escape
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String nz(String v) { return v == null ? "" : v; }

    /** Schema freshness 비교 — DictionaryController.freshness 노출. */
    public Map<String, Object> compareWithSchema() {
        List<DataDictionary> registered = mapper.findAll();
        List<DataDictionaryMapper.SchemaColumn> actual = mapper.findActualSchemaColumns();

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("registeredCount", registered.size());
        ret.put("actualCount", actual.size());

        // simple: count of actual columns that are not registered
        java.util.Set<String> regKeys = new java.util.HashSet<>();
        for (DataDictionary d : registered) {
            regKeys.add(d.getTableName() + "." + d.getColumnName());
        }
        int missing = 0;
        java.util.List<String> missingList = new java.util.ArrayList<>();
        for (DataDictionaryMapper.SchemaColumn c : actual) {
            if (!regKeys.contains(c.getTableName() + "." + c.getColumnName())) {
                missing++;
                if (missingList.size() < 50) {
                    missingList.add(c.getTableName() + "." + c.getColumnName());
                }
            }
        }
        ret.put("missingInDictionary", missing);
        ret.put("missingSamples", missingList);
        return ret;
    }

    private void recordHistory(DataDictionary before, DataDictionary updated,
                                String fieldName, String oldVal, String newVal,
                                Long changedBy) {
        if (java.util.Objects.equals(oldVal, newVal)) return;
        DataDictionaryHistory h = DataDictionaryHistory.builder()
                .dictionaryId(before.getId())
                .fieldChanged(fieldName)
                .oldValue(oldVal)
                .newValue(newVal)
                .changedBy(changedBy)
                .build();
        mapper.insertHistory(h);
    }
}
