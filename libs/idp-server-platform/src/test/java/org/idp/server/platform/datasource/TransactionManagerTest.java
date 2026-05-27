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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TransactionManager")
class TransactionManagerTest {

  @AfterEach
  void cleanUp() {
    // ThreadLocal リーク防止: 各テスト後に必ず close
    try {
      TransactionManager.closeConnection();
    } catch (Exception ignored) {
      // テスト失敗時の保険、無視してよい
    }
  }

  @Nested
  @DisplayName("endReadTransaction")
  class EndReadTransactionTest {

    @Test
    @DisplayName("connectionHolder が空の場合は no-op で例外を投げない")
    void noOpWhenConnectionHolderIsEmpty() {
      assertDoesNotThrow(() -> TransactionManager.endReadTransaction());
    }

    @Test
    @DisplayName("正常時: commit() と close() が両方呼ばれる")
    void commitAndCloseOnSuccess() throws Exception {
      Connection mockConn = mock(Connection.class);
      DbConnectionProvider mockProvider = mock(DbConnectionProvider.class);
      when(mockProvider.getConnection(any(DatabaseType.class), anyBoolean())).thenReturn(mockConn);
      TransactionManager.configure(mockProvider);

      // admin=true 経由で createConnection し、setTenantId をスキップ
      TransactionManager.createConnection(DatabaseType.POSTGRESQL);

      TransactionManager.endReadTransaction();

      verify(mockConn, times(1)).commit();
      verify(mockConn, times(1)).close();
    }

    @Test
    @DisplayName("commit 失敗時: SqlRuntimeException がスローされ、かつ close() は呼ばれる (ThreadLocal リーク防止)")
    void throwsAndClosesOnCommitFailure() throws Exception {
      Connection mockConn = mock(Connection.class);
      doThrow(new SQLException("commit failed")).when(mockConn).commit();
      DbConnectionProvider mockProvider = mock(DbConnectionProvider.class);
      when(mockProvider.getConnection(any(DatabaseType.class), anyBoolean())).thenReturn(mockConn);
      TransactionManager.configure(mockProvider);

      TransactionManager.createConnection(DatabaseType.POSTGRESQL);

      assertThrows(SqlRuntimeException.class, () -> TransactionManager.endReadTransaction());

      // commit が失敗しても close は finally 経由で必ず呼ばれる
      verify(mockConn, times(1)).close();
    }
  }
}
