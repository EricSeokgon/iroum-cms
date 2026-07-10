package kr.co.ircp.cms.domain.email.template.admin.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL JSONB ↔ {@code List<Map<String, Object>>} 변환 TypeHandler.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 — email_template.variables(필수 변수 정의 배열) 전용.
 *
 * <p>auto-register(@MappedTypes) 하지 않는다 — 기존 {@code JsonListTypeHandler}(List&lt;String&gt;)와
 * List.class 매핑이 충돌하므로, 매퍼 XML에서 컬럼별로 명시 지정한다.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-EMAIL-TEMPLATE-001 — variables JSONB 전용. 매퍼 XML에서 명시 지정(auto-register 금지)
public class JsonbObjectListTypeHandler extends BaseTypeHandler<List<Map<String, Object>>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> TYPE = new TypeReference<>() {};

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Map<String, Object>> parameter,
                                    JdbcType jdbcType) throws SQLException {
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        try {
            obj.setValue(MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            obj.setValue("[]");
        }
        ps.setObject(i, obj);
    }

    @Override
    public List<Map<String, Object>> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<Map<String, Object>> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<Map<String, Object>> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<Map<String, Object>> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
