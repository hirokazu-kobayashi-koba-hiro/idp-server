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

import java.util.function.Supplier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Programmatic transaction helpers for {@link NoTransaction} methods.
 *
 * <p>Each helper opens a short transaction, runs the supplier, and releases the connection — so a
 * single request can interleave several independent transactions with non-DB work in between (e.g.
 * an external HTTP call). Mirrors what {@code TenantAwareEntryServiceProxy} does for an
 * {@code @Transaction} method, scoped to a single block.
 *
 * <p>Requires {@link #configure(ApplicationDatabaseTypeProvider)} to be called once at startup so
 * the helpers can resolve the configured {@link DatabaseType}.
 */
public final class Transactions {

  private static ApplicationDatabaseTypeProvider databaseTypeProvider;

  private Transactions() {}

  public static void configure(ApplicationDatabaseTypeProvider provider) {
    databaseTypeProvider = provider;
  }

  /**
   * Open a short read-only transaction, run {@code action}, and release the connection. Commits the
   * read so HikariCP does not record a spurious rollback (see {@link
   * TransactionManager#endReadTransaction()}).
   */
  public static <T> T readOnly(TenantIdentifier tenantIdentifier, Supplier<T> action) {
    DatabaseType databaseType = requireProvider().provide();
    TransactionManager.createConnection(databaseType, tenantIdentifier);
    try {
      OperationContext.set(OperationType.READ);
      T result = action.get();
      TransactionManager.endReadTransaction();
      return result;
    } catch (RuntimeException e) {
      TransactionManager.closeConnection();
      throw e;
    } finally {
      OperationContext.clear();
    }
  }

  /**
   * Open a short write transaction, run {@code action}, and commit. On any {@link
   * RuntimeException}, rollback before propagating.
   */
  public static <T> T write(TenantIdentifier tenantIdentifier, Supplier<T> action) {
    DatabaseType databaseType = requireProvider().provide();
    TransactionManager.beginTransaction(databaseType, tenantIdentifier);
    try {
      OperationContext.set(OperationType.WRITE);
      T result = action.get();
      TransactionManager.commitTransaction();
      return result;
    } catch (RuntimeException e) {
      TransactionManager.rollbackTransaction();
      throw e;
    } finally {
      OperationContext.clear();
    }
  }

  private static ApplicationDatabaseTypeProvider requireProvider() {
    if (databaseTypeProvider == null) {
      throw new IllegalStateException(
          "Transactions has not been configured. Call Transactions.configure(provider) at startup.");
    }
    return databaseTypeProvider;
  }
}
