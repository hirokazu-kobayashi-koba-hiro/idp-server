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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.user.UserIdentifier;

public class YearlyActiveUserMysqlExecutor implements YearlyActiveUserSqlExecutor {

  @Override
  public void addActiveUser(TenantIdentifier tenantId, String statYear, UserIdentifier userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                INSERT INTO statistics_yearly_users (
                    tenant_id,
                    stat_year,
                    user_id,
                    last_used_at,
                    created_at
                ) VALUES (
                    ?,
                    ?,
                    ?,
                    NOW(),
                    NOW()
                )
                ON DUPLICATE KEY UPDATE last_used_at = NOW()
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statYear);
    params.add(userId.value());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId, String statYear, UserIdentifier userId) {
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
    addActiveUser(tenantId, statYear, userId);

    return isNew;
  }

  @Override
  public LocalDateTime getLastUsedAt(
      TenantIdentifier tenantId, String statYear, UserIdentifier userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                SELECT last_used_at
                FROM statistics_yearly_users
                WHERE tenant_id = ? AND stat_year = ? AND user_id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statYear);
    params.add(userId.value());

    Map<String, String> result = sqlExecutor.selectOne(sql, params);
    if (result.isEmpty()) {
      return null;
    }

    String lastUsedAtStr = result.get("last_used_at");
    if (lastUsedAtStr != null && !lastUsedAtStr.isEmpty()) {
      return Timestamp.valueOf(lastUsedAtStr).toLocalDateTime();
    }
    return null;
  }

  @Override
  public void deleteByYear(TenantIdentifier tenantId, String statYear) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                DELETE FROM statistics_yearly_users
                WHERE tenant_id = ? AND stat_year = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statYear);

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void deleteOlderThan(String beforeYear) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                DELETE FROM statistics_yearly_users
                WHERE stat_year < ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(beforeYear);

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void deleteByTenantId(TenantIdentifier tenantId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                DELETE FROM statistics_yearly_users
                WHERE tenant_id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public int getYauCount(TenantIdentifier tenantId, String statYear) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                SELECT COUNT(*) as yau_count
                FROM statistics_yearly_users
                WHERE tenant_id = ? AND stat_year = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statYear);

    Map<String, Object> result = sqlExecutor.selectOneWithType(sql, params);

    if (result.isEmpty()) {
      return 0;
    }

    Object yauCountObj = result.get("yau_count");
    if (yauCountObj instanceof Long longValue) {
      return longValue.intValue();
    } else if (yauCountObj instanceof Integer intValue) {
      return intValue;
    }

    return 0;
  }
}
