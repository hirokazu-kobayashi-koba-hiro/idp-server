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

package org.idp.server.core.adapters.datasource.federation.session.operation;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;

public class MysqlExecutor implements SsoSessionOperationSqlExecutor {

  @Override
  public int deleteExpired(int limit) {
    // federation_sso_session has no expires_at column; SSO sessions complete within minutes,
    // so anything older than 1 hour is abandoned.
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM federation_sso_session
            WHERE created_at < (now() - INTERVAL 1 HOUR)
            LIMIT ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(limit);

    return sqlExecutor.executeAndReturnAffectedRows(sqlTemplate, params);
  }
}
