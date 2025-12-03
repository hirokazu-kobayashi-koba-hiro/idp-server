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

package org.idp.server.core.adapters.datasource.statistics.command;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.user.UserIdentifier;

public class DailyActiveUserPostgresqlExecutor implements DailyActiveUserSqlExecutor {

  @Override
  public void addActiveUser(TenantIdentifier tenantId, LocalDate date, UserIdentifier userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                INSERT INTO statistics_daily_users (
                    tenant_id,
                    stat_date,
                    user_id,
                    created_at
                ) VALUES (
                    ?::uuid,
                    ?,
                    ?::uuid,
                    NOW()
                )
                ON CONFLICT (tenant_id, stat_date, user_id) DO NOTHING
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(date);
    params.add(userId.value());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId, LocalDate date, UserIdentifier userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                INSERT INTO statistics_daily_users (
                    tenant_id,
                    stat_date,
                    user_id,
                    created_at
                ) VALUES (
                    ?::uuid,
                    ?,
                    ?::uuid,
                    NOW()
                )
                ON CONFLICT (tenant_id, stat_date, user_id) DO NOTHING
                RETURNING user_id
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(date);
    params.add(userId.value());

    return sqlExecutor.executeAndCheckReturned(sql, params);
  }

  @Override
  public void deleteByDate(TenantIdentifier tenantId, LocalDate date) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                DELETE FROM statistics_daily_users
                WHERE tenant_id = ?::uuid AND stat_date = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(date);

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void deleteOlderThan(LocalDate before) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                DELETE FROM statistics_daily_users
                WHERE stat_date < ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(before);

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void deleteByTenantId(TenantIdentifier tenantId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                DELETE FROM statistics_daily_users
                WHERE tenant_id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public int getDauCount(TenantIdentifier tenantId, LocalDate date) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                SELECT COUNT(*) as dau_count
                FROM statistics_daily_users
                WHERE tenant_id = ?::uuid AND stat_date = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(date);

    Map<String, Object> result = sqlExecutor.selectOneWithType(sql, params);

    if (result.isEmpty()) {
      return 0;
    }

    Object dauCountObj = result.get("dau_count");
    if (dauCountObj instanceof Long longValue) {
      return longValue.intValue();
    } else if (dauCountObj instanceof Integer intValue) {
      return intValue;
    }

    return 0;
  }
}
