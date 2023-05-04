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

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 */
// 类型处理器(处理器模式)
public interface TypeHandler<T> {
  // 设置 PreparedStatement 指定参数
  // JavaType -> JdbcType
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * @param columnName Colunm name, when configuration <code>useColumnLabel</code> is <code>false</code>
   */
  // 获得 ResultSet 的指定字段的值(字段名)
  // JdbcType -> JavaType
  T getResult(ResultSet rs, String columnName) throws SQLException;
  // 获得 ResultSet 的指定字段的值(字段值)
  // JdbcType -> JavaType
  T getResult(ResultSet rs, int columnIndex) throws SQLException;
  // 获得 CallableStatement 的指定字段的值
  // JdbcType -> JavaType
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
