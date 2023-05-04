/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.type;

import org.apache.ibatis.io.Resources;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Clinton Begin
 */
// 未知类型 处理器
public class UnknownTypeHandler extends BaseTypeHandler<Object> {
  // 单例的 ObjectTypeHandler
  private static final ObjectTypeHandler OBJECT_TYPE_HANDLER = new ObjectTypeHandler();
  // TypeHandler 注册表
  private TypeHandlerRegistry typeHandlerRegistry;

  public UnknownTypeHandler(TypeHandlerRegistry typeHandlerRegistry) {
    this.typeHandlerRegistry = typeHandlerRegistry;
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
      throws SQLException {
    // 获取参数对应的类型处理器
    TypeHandler handler = resolveTypeHandler(parameter, jdbcType);
    // 调用实际的 TypeHandler, 设置参数
    handler.setParameter(ps, i, parameter, jdbcType);
  }

  @Override
  public Object getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    // 获取结果字段对应的类型处理器
    TypeHandler<?> handler = resolveTypeHandler(rs, columnName);
    // 调用实际的 TypeHandler, 获取结果
    return handler.getResult(rs, columnName);
  }

  @Override
  public Object getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    // 获取结果字段对应的类型处理器
    TypeHandler<?> handler = resolveTypeHandler(rs.getMetaData(), columnIndex);
    if (handler == null || handler instanceof UnknownTypeHandler) {
      // 如果没解析获取到对应的类型处理器, 使用默认的 ObjectTypeHandler
      handler = OBJECT_TYPE_HANDLER;
    }
    // 调用实际的 TypeHandler, 获取结果
    return handler.getResult(rs, columnIndex);
  }

  @Override
  public Object getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    return cs.getObject(columnIndex);
  }
  // 获取参数对应的类型处理器
  private TypeHandler<?> resolveTypeHandler(Object parameter, JdbcType jdbcType) {
    TypeHandler<?> handler;
    // 如果参数为空, 返回 OBJECT_TYPE_HANDLER
    if (parameter == null) {
      handler = OBJECT_TYPE_HANDLER;
    } else {
      // 从类型处理器注册表获取对应的类型处理器
      handler = typeHandlerRegistry.getTypeHandler(parameter.getClass(), jdbcType);
      // check if handler is null (issue #270)
      if (handler == null || handler instanceof UnknownTypeHandler) {
        // 如果还没获取到对应的类型处理器, 使用默认的 OBJECT_TYPE_HANDLER
        handler = OBJECT_TYPE_HANDLER;
      }
    }
    return handler;
  }
  // 获取结果字段对应的类型处理器
  private TypeHandler<?> resolveTypeHandler(ResultSet rs, String column) {
    try {
      Map<String,Integer> columnIndexLookup;
      columnIndexLookup = new HashMap<>();
      // 获取 ResultSet 的 MetaData属性
      ResultSetMetaData rsmd = rs.getMetaData();
      int count = rsmd.getColumnCount();
      for (int i = 1; i <= count; i++) {
        String name = rsmd.getColumnName(i);
        // 添加 column name -> columnIndex 的映射关系
        columnIndexLookup.put(name,i);
      }
      // 获取 columnIndex
      Integer columnIndex = columnIndexLookup.get(column);
      TypeHandler<?> handler = null;
      if (columnIndex != null) {
        // 如果 columnIndex 不为 null, 解析对应的类型处理器
        handler = resolveTypeHandler(rsmd, columnIndex);
      }
      if (handler == null || handler instanceof UnknownTypeHandler) {
        // 如果 没有获取到类型处理器, 使用默认的 OBJECT_TYPE_HANDLER
        handler = OBJECT_TYPE_HANDLER;
      }
      return handler;
    } catch (SQLException e) {
      throw new TypeException("Error determining JDBC type for column " + column + ".  Cause: " + e, e);
    }
  }
  // 获取结果字段对应的类型处理器
  private TypeHandler<?> resolveTypeHandler(ResultSetMetaData rsmd, Integer columnIndex) {
    TypeHandler<?> handler = null;
    // 从 ResultSetMetaData 中,根据 columnIndex 获取对应的 JdbcType
    JdbcType jdbcType = safeGetJdbcTypeForColumn(rsmd, columnIndex);
    // 从 ResultSetMetaData 中,根据 columnIndex 获取对应的 JavaType
    Class<?> javaType = safeGetClassForColumn(rsmd, columnIndex);
    if (javaType != null && jdbcType != null) {
      // 从类型处理器注册表获取对应的类型处理器
      handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
    } else if (javaType != null) {
      // 从类型处理器注册表获取对应的类型处理器
      handler = typeHandlerRegistry.getTypeHandler(javaType);
    } else if (jdbcType != null) {
      // 从类型处理器注册表获取对应的类型处理器
      handler = typeHandlerRegistry.getTypeHandler(jdbcType);
    }
    return handler;
  }
  // 从 ResultSetMetaData 中,根据 columnIndex 获取对应的 JdbcType
  private JdbcType safeGetJdbcTypeForColumn(ResultSetMetaData rsmd, Integer columnIndex) {
    try {
      // 从 jdbcType code 获取 JdbcType
      return JdbcType.forCode(rsmd.getColumnType(columnIndex));
    } catch (Exception e) {
      return null;
    }
  }
  // 从 ResultSetMetaData 中,根据 columnIndex 获取对应的 JavaType
  private Class<?> safeGetClassForColumn(ResultSetMetaData rsmd, Integer columnIndex) {
    try {
      // 从全限定名获取 Class
      return Resources.classForName(rsmd.getColumnClassName(columnIndex));
    } catch (Exception e) {
      return null;
    }
  }
}
