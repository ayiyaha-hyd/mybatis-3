/**
 *    Copyright 2009-2018 the original author or authors.
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
// Integer 类型处理器(java.lang.Integer <-> int)
public class IntegerTypeHandler extends BaseTypeHandler<Integer> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Integer parameter, JdbcType jdbcType)
      throws SQLException {
    // 直接设置参数
    ps.setInt(i, parameter);
  }

  @Override
  public Integer getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    // 获取 int 类型结果
    int result = rs.getInt(columnName);
    // 结果为 null 返回 null(此处当结果为 null 时, 排除 int 默认 0的干扰,返回实际 null)
    return result == 0 && rs.wasNull() ? null : result;
  }

  @Override
  public Integer getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    // 获取 int 类型结果
    int result = rs.getInt(columnIndex);
    // 结果为 null 返回 null(此处当结果为 null 时, 排除 int 默认 0的干扰,返回实际 null)
    return result == 0 && rs.wasNull() ? null : result;
  }

  @Override
  public Integer getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    // 获取 int 类型结果
    int result = cs.getInt(columnIndex);
    // 结果为 null 返回 null(此处当结果为 null 时, 排除 int 默认 0的干扰,返回实际 null)
    return result == 0 && cs.wasNull() ? null : result;
  }
}
