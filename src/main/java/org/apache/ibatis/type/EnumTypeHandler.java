/**
 *    Copyright 2009-2017 the original author or authors.
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

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 */
// Enum 类型处理器(Enum.name <-> String)
public class EnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {
  // E 对应的枚举类
  private final Class<E> type;

  public EnumTypeHandler(Class<E> type) {
    if (type == null) {
      throw new IllegalArgumentException("Type argument cannot be null");
    }
    this.type = type;
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
    // 如果 jdbcType 类型为空
    if (jdbcType == null) {
      // jdbcType 为 null 时, 默认 jdbcType 为 string
      // 使用 Enum.name() 的值
      // 将 java.lang.Enum 转换为 java.lang.String 类型
      // 然后设置参数
      ps.setString(i, parameter.name());
    } else {
      // 设置参数
      ps.setObject(i, parameter.name(), jdbcType.TYPE_CODE); // see r3589
    }
  }

  @Override
  public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
    // 获取 string 值
    String s = rs.getString(columnName);
    // 将 java.lang.String 类型 转换为 java.lang.Enum 类型
    return s == null ? null : Enum.valueOf(type, s);
  }

  @Override
  public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    // 获取 string 值
    String s = rs.getString(columnIndex);
    // 将 java.lang.String 类型 转换为 java.lang.Enum 类型
    return s == null ? null : Enum.valueOf(type, s);
  }

  @Override
  public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    // 获取 string 值
    String s = cs.getString(columnIndex);
    // 将 java.lang.String 类型 转换为 java.lang.Enum 类型
    return s == null ? null : Enum.valueOf(type, s);
  }
}
