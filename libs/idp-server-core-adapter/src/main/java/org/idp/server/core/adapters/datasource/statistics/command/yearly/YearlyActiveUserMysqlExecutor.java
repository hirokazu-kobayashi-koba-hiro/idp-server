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

package org.idp.server.core.adapters.datasource.statistics.command.yearly;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.event.SecurityEventUserIdentifier;

public class YearlyActiveUserMysqlExecutor implements YearlyActiveUserSqlExecutor {

  @Override
  public boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId,
      LocalDate statYear,
      SecurityEventUserIdentifier userId,
      String userName) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // MySQL doesn't support RETURNING, so we check if the record exists first
    String checkSql =
        """
                SELECT 1 FROM statistics_yearly_users
                WHERE tenant_id = ? AND stat_year = ? AND user_id = ?
                """;

    List<Object> checkParams = new ArrayList<>();
    checkParams.add(tenantId.value());
    checkParams.add(statYear);
    checkParams.add(userId.value());

    Map<String, String> existing = sqlExecutor.selectOne(checkSql, checkParams);
    boolean isNew = existing.isEmpty();

    // Insert or update
    String sql =
        """
                INSERT INTO statistics_yearly_users (
                    tenant_id,
                    stat_year,
                    user_id,
                    user_name,
                    last_used_at,
                    created_at
                ) VALUES (?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE last_used_at = NOW(6)
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statYear);
    params.add(userId.value());
    params.add(userName != null ? userName : "");

    sqlExecutor.execute(sql, params);

    return isNew;
  }
}
