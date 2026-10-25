package com.muite.zongshe1.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.entity.Point;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

// 自定义JSON类型处理器：List<Point> <-> JSON字符串
public class PointListTypeHandler extends BaseTypeHandler<List<Point>> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Point> parameter, JdbcType jdbcType) throws SQLException {
        // 序列化：List<Point> -> JSON字符串
        try {
            String json = objectMapper.writeValueAsString(parameter);
            ps.setString(i, json);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize List<Point> to JSON", e);
        }
    }

    @Override
    public List<Point> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 反序列化：JSON字符串 -> List<Point>
        return parseJson(rs.getString(columnName));
    }

    @Override
    public List<Point> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public List<Point> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    // 解析JSON字符串为List<Point>
    private List<Point> parseJson(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            // 注意：指定泛型类型为List<Point>
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Point.class));
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize JSON to List<Point>", e);
        }
    }
}