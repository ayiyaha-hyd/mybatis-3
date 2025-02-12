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
package org.apache.ibatis.transaction.managed;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link Transaction} that lets the container manage the full lifecycle of the transaction.
 * Delays connection retrieval until getConnection() is called.
 * Ignores all commit or rollback requests.
 * By default, it closes the connection but can be configured not to do it.
 *
 * @author Clinton Begin
 *
 * @see ManagedTransactionFactory
 */
// 基于容器管理的事务实现类
// 此类里边的提交和回滚都是空方法,需要容器自己继承该类去实现这些方法,例如(SpringManagedTransaction)
public class ManagedTransaction implements Transaction {

  private static final Log log = LogFactory.getLog(ManagedTransaction.class);
  // 数据源
  private DataSource dataSource;
  // 事务隔离级别
  private TransactionIsolationLevel level;
  // 数据库连接
  private Connection connection;
  // 连接是否关闭
  private final boolean closeConnection;
  // 构造方法
  public ManagedTransaction(Connection connection, boolean closeConnection) {
    this.connection = connection;
    this.closeConnection = closeConnection;
  }
  // 构造方法
  public ManagedTransaction(DataSource ds, TransactionIsolationLevel level, boolean closeConnection) {
    this.dataSource = ds;
    this.level = level;
    this.closeConnection = closeConnection;
  }
  // 获取连接
  @Override
  public Connection getConnection() throws SQLException {
    // 如果当前连接为 null, 则创建连接
    if (this.connection == null) {
      // 创建连接
      openConnection();
    }
    return this.connection;
  }
  // 事务提交(空实现,交给容器管理)**
  @Override
  public void commit() throws SQLException {
    // Does nothing
  }
  // 事务回滚(空实现,交给容器管理)**
  @Override
  public void rollback() throws SQLException {
    // Does nothing
  }

  @Override
  public void close() throws SQLException {
    if (this.closeConnection && this.connection != null) {
      if (log.isDebugEnabled()) {
        log.debug("Closing JDBC Connection [" + this.connection + "]");
      }
      this.connection.close();
    }
  }
  // 创建连接
  protected void openConnection() throws SQLException {
    if (log.isDebugEnabled()) {
      log.debug("Opening JDBC Connection");
    }
    // 获取连接
    this.connection = this.dataSource.getConnection();
    if (this.level != null) {
      // 设置连接的事务隔离级别
      this.connection.setTransactionIsolation(this.level.getLevel());
    }
  }

  @Override
  public Integer getTimeout() throws SQLException {
    return null;
  }

}
