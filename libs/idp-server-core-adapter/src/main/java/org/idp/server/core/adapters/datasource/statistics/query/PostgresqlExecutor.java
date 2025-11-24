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

package org.idp.server.core.adapters.datasource.statistics.query;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsDataIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;

public class PostgresqlExecutor implements TenantStatisticsDataSqlExecutor {

  @Override
  public Map<String, String> selectOne(TenantStatisticsDataIdentifier id) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
                SELECT id, tenant_id, stat_date, metrics, created_at
                FROM tenant_statistics_data
                WHERE id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(id.value());

    return sqlExecutor.selectOne(sql, params);
  }

  @Override
  public Map<String, String> selectByDate(TenantIdentifier tenantId, LocalDate date) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
                SELECT id, tenant_id, stat_date, metrics, created_at
                FROM tenant_statistics_data
                WHERE tenant_id = ?::uuid AND stat_date = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(date);

    return sqlExecutor.selectOne(sql, params);
  }

  @Override
  public List<Map<String, String>> selectByDateRange(
      TenantIdentifier tenantId, TenantStatisticsQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
                SELECT id, tenant_id, stat_date, metrics, created_at
                FROM tenant_statistics_data
                WHERE tenant_id = ?::uuid
                  AND stat_date >= ?
                  AND stat_date <= ?
                ORDER BY stat_date DESC
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(queries.fromAsLocalDate());
    params.add(queries.toAsLocalDate());

    return sqlExecutor.selectList(sql, params);
  }

  @Override
  public Map<String, String> selectCount(TenantIdentifier tenantId, LocalDate from, LocalDate to) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
                SELECT COUNT(*) as count
                FROM tenant_statistics_data
                WHERE tenant_id = ?::uuid
                  AND stat_date >= ?
                  AND stat_date <= ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(from);
    params.add(to);

    return sqlExecutor.selectOne(sql, params);
  }

  @Override
  public Map<String, String> selectLatest(TenantIdentifier tenantId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
                SELECT id, tenant_id, stat_date, metrics, created_at
                FROM tenant_statistics_data
                WHERE tenant_id = ?::uuid
                ORDER BY stat_date DESC
                LIMIT 1
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());

    return sqlExecutor.selectOne(sql, params);
  }

  @Override
  public Map<String, String> selectExists(TenantIdentifier tenantId, LocalDate date) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
                SELECT COUNT(*) as count
                FROM tenant_statistics_data
                WHERE tenant_id = ?::uuid AND stat_date = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(date);

    return sqlExecutor.selectOne(sql, params);
  }
}
