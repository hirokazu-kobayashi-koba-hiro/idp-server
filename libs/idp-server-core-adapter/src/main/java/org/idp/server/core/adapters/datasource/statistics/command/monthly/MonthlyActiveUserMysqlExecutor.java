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

package org.idp.server.core.adapters.datasource.statistics.command.monthly;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.event.SecurityEventUserIdentifier;

public class MonthlyActiveUserMysqlExecutor implements MonthlyActiveUserSqlExecutor {

  @Override
  public boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId,
      LocalDate statMonth,
      SecurityEventUserIdentifier userId,
      String userName) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // MySQL: Use INSERT IGNORE and check affected rows to detect if new row was inserted
    // Returns 1 if inserted, 0 if duplicate was ignored
    String sql =
        """
                INSERT IGNORE INTO statistics_monthly_users (
                    tenant_id,
                    stat_month,
                    user_id,
                    user_name,
                    created_at
                ) VALUES (?, ?, ?, ?, NOW(6))
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statMonth);
    params.add(userId.value());
    params.add(userName != null ? userName : "");

    int affectedRows = sqlExecutor.executeAndReturnAffectedRows(sql, params);
    return affectedRows > 0;
  }
}
