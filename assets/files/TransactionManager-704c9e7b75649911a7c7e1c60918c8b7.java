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
