/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class TransactionManager {
  private static final LoggerWrapper log = LoggerWrapper.getLogger(TransactionManager.class);
  private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
  private static DbConnectionProvider dbConnectionProvider;

  public static void configure(DbConnectionProvider provider) {
    dbConnectionProvider = provider;
  }

  public static void createConnection(
      DatabaseType databaseType, TenantIdentifier tenantIdentifier) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.READ);
    Connection conn =
        dbConnectionProvider.getConnection(
            databaseType, AdminTenantContext.isAdmin(tenantIdentifier));
    if (databaseType == DatabaseType.POSTGRESQL) {
      setTenantId(conn, tenantIdentifier);
    }
    connectionHolder.set(conn);
  }

  public static void createConnection(DatabaseType databaseType) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.READ);
    Connection conn = dbConnectionProvider.getConnection(databaseType, true);
    connectionHolder.set(conn);
  }

  public static void beginTransaction(
      DatabaseType databaseType, TenantIdentifier tenantIdentifier) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.WRITE);
    Connection conn =
        dbConnectionProvider.getConnection(
            databaseType, AdminTenantContext.isAdmin(tenantIdentifier));
    if (databaseType == DatabaseType.POSTGRESQL) {
      setTenantId(conn, tenantIdentifier);
    }
    connectionHolder.set(conn);
  }

  public static void beginTransaction(DatabaseType databaseType) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.WRITE);
    Connection conn = dbConnectionProvider.getConnection(databaseType, true);
    connectionHolder.set(conn);
  }

  public static Connection getConnection() {
    Connection conn = connectionHolder.get();
    if (conn == null) {
      throw new SqlRuntimeException("No active transaction");
    }
    return conn;
  }

  public static void commitTransaction() {
    Connection conn = connectionHolder.get();
    if (conn == null) return;
    try {
      conn.commit();
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to commit transaction", e);
    } finally {
      closeConnection();
    }
  }

  public static void rollbackTransaction() {
    Connection conn = connectionHolder.get();
    if (conn == null) return;
    try {
      conn.rollback();
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to rollback transaction", e);
    } finally {
      closeConnection();
    }
  }

  /**
   * READ パス専用の終了処理。
   *
   * <p>HikariCP の {@code autoCommit=false} 設定下では、{@link #setTenantId} の {@code set_config()}
   * 呼び出し（または最初の SELECT）で暗黙的に transaction が開始される。READ パスでも明示的に {@code commit()} を呼ばずに connection を
   * close すると、HikariCP が pool 返却時に強制 rollback を発行し、特に reader (replica) DB の {@code
   * pg_stat_database.xact_rollback} が READ 操作ごとに加算されてしまう（commit 成功率メトリクスの汚染）。
   *
   * <p>このメソッドは READ パスでも {@code commit()} を発行することで、上記のメトリクス汚染を防ぐ。
   *
   * <p><strong>例外時に explicit rollback を呼ばない理由</strong>: explicit rollback を発行しても、HikariCP が close
   * 時に reset 処理として rollback を発行しても、どちらも DB 側では同じ 1 件の {@code xact_rollback} としてカウントされる。READ
   * パスの例外は元々まれであり、本 PR の主目的である「正常系の commit 集計回復」には寄与しない。シンプルさのため {@code finally} 側の {@link
   * #closeConnection} 経由で HikariCP の reset rollback に任せる構造を採用している（WRITE パスは正常系・例外系の意味論的差を明示するため
   * explicit に commit/rollback を発行する）。
   *
   * @see <a href="https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1552">Issue
   *     #1552</a>
   */
  public static void endReadTransaction() {
    Connection conn = connectionHolder.get();
    if (conn == null) return;
    try {
      conn.commit();
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to commit read transaction", e);
    } finally {
      closeConnection();
    }
  }

  public static void closeConnection() {
    Connection conn = connectionHolder.get();
    if (conn != null) {
      try {
        conn.close();
      } catch (SQLException e) {
        throw new SqlRuntimeException("Failed to close connection", e);
      } finally {
        connectionHolder.remove();
        OperationContext.clear();
      }
    }
  }

  /**
   * Sets the current tenant identifier for Row-Level Security (RLS) using PostgreSQL's {@code
   * set_config()} function.
   *
   * <p>This method assigns the tenant ID to the session variable {@code app.tenant_id}, which is
   * used in RLS policies via {@code current_setting('app.tenant_id', true)}.
   *
   * <p><strong>Important:</strong>
   *
   * <ul>
   *   <li>Use {@code is_local = true} in {@code set_config(...)} to ensure the setting is
   *       <em>transaction-local</em> and automatically cleared when the transaction ends.
   *   <li>Using {@code is_local = false} would persist the value for the entire session, which is
   *       dangerous when using a connection pool — the tenant ID could <em>leak across tenants</em>
   *       through reused connections.
   *   <li>This method must be called <em>after</em> a transaction has started. If auto-commit is
   *       enabled, each statement runs in its own transaction, causing {@code set_config(...)} to
   *       reset immediately and the setting will not apply to subsequent queries.
   * </ul>
   *
   * <p>Reference: <a
   * href="https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADMIN-SET">
   * PostgreSQL Documentation: Configuration Settings Functions</a>
   *
   * @param conn active SQL connection (must be within an open transaction)
   * @param tenantIdentifier the tenant ID to set in the session context
   * @throws SqlRuntimeException if the tenant ID could not be set
   */
  private static void setTenantId(Connection conn, TenantIdentifier tenantIdentifier) {
    log.trace("[RLS] SET app.tenant_id: tenant={}", tenantIdentifier.value());

    // Use set_config() function with PreparedStatement to prevent SQL Injection
    // See: https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADMIN-SET
    try (var stmt = conn.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
      stmt.setString(1, tenantIdentifier.value());
      stmt.execute();
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to set tenant_id", e);
    }
  }
}
