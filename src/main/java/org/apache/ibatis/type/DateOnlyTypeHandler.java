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
import java.util.Date;

/**
 * @author Clinton Begin
 */
// Date 类型处理器(java.util.Date <-> java.sql.Date)
public class DateOnlyTypeHandler extends BaseTypeHandler<Date> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Date parameter, JdbcType jdbcType)
      throws SQLException {
    // 将 java.util.Date 转换为 java.sql.Date 类型
    // 然后设置参数
    ps.setDate(i, new java.sql.Date(parameter.getTime()));
  }

  @Override
  public Date getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    // 获取 java.sql.Date 值
    java.sql.Date sqlDate = rs.getDate(columnName);
    if (sqlDate != null) {
      // 将 java.sql.Date 转换为 java.util.Date 类型
      return new Date(sqlDate.getTime());
    }
    return null;
  }

  @Override
  public Date getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    // 获取 java.sql.Date 值
    java.sql.Date sqlDate = rs.getDate(columnIndex);
    if (sqlDate != null) {
      // 将 java.sql.Date 转换为 java.util.Date 类型
      return new Date(sqlDate.getTime());
    }
    return null;
  }

  @Override
  public Date getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    // 获取 java.sql.Date 值
    java.sql.Date sqlDate = cs.getDate(columnIndex);
    if (sqlDate != null) {
      // 将 java.sql.Date 转换为 java.util.Date 类型
      return new Date(sqlDate.getTime());
    }
    return null;
  }

}
