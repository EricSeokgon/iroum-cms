package kr.co.ircp.cms.domain.board.repository;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL UUID[] ↔ List&lt;UUID&gt; 변환 MyBatis TypeHandler.
 * REQ-BOARD-012-D-4: publication_zip_archive.asset_uuids 컬럼에서 사용.
 */
@MappedJdbcTypes(JdbcType.ARRAY)
public class UuidArrayTypeHandler extends BaseTypeHandler<List<UUID>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<UUID> parameter,
                                    JdbcType jdbcType) throws SQLException {
        // UUID[]를 PostgreSQL UUID 배열로 변환
        UUID[] uuids = parameter.toArray(new UUID[0]);
        Array array = ps.getConnection().createArrayOf("uuid", uuids);
        ps.setArray(i, array);
    }

    @Override
    public List<UUID> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toList(rs.getArray(columnName));
    }

    @Override
    public List<UUID> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toList(rs.getArray(columnIndex));
    }

    @Override
    public List<UUID> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toList(cs.getArray(columnIndex));
    }

    private List<UUID> toList(Array array) throws SQLException {
        if (array == null) return Collections.emptyList();
        Object result = array.getArray();
        if (result instanceof UUID[] uuids) {
            List<UUID> list = new ArrayList<>(uuids.length);
            Collections.addAll(list, uuids);
            return list;
        }
        if (result instanceof Object[] objects) {
            List<UUID> list = new ArrayList<>(objects.length);
            for (Object o : objects) {
                if (o instanceof UUID uuid) {
                    list.add(uuid);
                } else if (o != null) {
                    list.add(UUID.fromString(o.toString()));
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
