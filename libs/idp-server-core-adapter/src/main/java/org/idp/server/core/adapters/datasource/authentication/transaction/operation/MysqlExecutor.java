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

package org.idp.server.core.adapters.datasource.authentication.transaction.operation;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;

public class MysqlExecutor implements AuthenticationTransactionSqlExecutor {

  @Override
  public void deleteExpiredTransaction(int limit) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM authentication_transaction
        WHERE expires_at < now()
        LIMIT ?;
    """;

    List<Object> params = new ArrayList<>();
    params.add(limit);

    sqlExecutor.execute(sqlTemplate, params);
  }
}
