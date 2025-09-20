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

  private static void setTenantId(Connection conn, TenantIdentifier tenantIdentifier) {
    log.debug("[RLS] SET app.tenant_id: tenant={}", tenantIdentifier.value());

    try (var stmt = conn.createStatement()) {
      stmt.execute("SET app.tenant_id = '" + tenantIdentifier.value() + "'");
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to set tenant_id", e);
    }
  }
}
