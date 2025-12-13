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
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.user.UserIdentifier;

public class YearlyActiveUserPostgresqlExecutor implements YearlyActiveUserSqlExecutor {

  @Override
  public boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId, LocalDate statYear, UserIdentifier userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Use ON CONFLICT DO NOTHING + RETURNING pattern (same as Daily/Monthly)
    // xmax system column cannot be accessed in partitioned tables
    String sql =
        """
                INSERT INTO statistics_yearly_users (
                    tenant_id,
                    stat_year,
                    user_id,
                    last_used_at,
                    created_at
                ) VALUES (
                    ?::uuid,
                    ?::date,
                    ?::uuid,
                    NOW(),
                    NOW()
                )
                ON CONFLICT (tenant_id, stat_year, user_id) DO NOTHING
                RETURNING user_id
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statYear);
    params.add(userId.value());

    // If RETURNING returns a row, the insert succeeded (new user)
    // If RETURNING returns empty, the user already existed (conflict)
    return sqlExecutor.executeAndCheckReturned(sql, params);
  }
}
