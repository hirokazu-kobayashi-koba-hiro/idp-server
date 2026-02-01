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

package org.idp.server.core.adapters.datasource.statistics.query.monthly;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * PostgreSQL executor for statistics queries.
 *
 * <p>This class is responsible only for executing SQL queries and returning raw data as Map. No
 * aggregation or business logic is implemented here.
 */
public class PostgresqlExecutor implements TenantStatisticsSqlExecutor {

  @Override
  public List<Map<String, String>> selectEvents(
      TenantIdentifier tenantId, LocalDate fromDate, LocalDate toDate) {

    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
            SELECT stat_date, event_type, count, created_at, updated_at
            FROM statistics_events
            WHERE tenant_id = ?::uuid
              AND stat_date >= ?::date
              AND stat_date < ?::date
            ORDER BY stat_date, event_type
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(fromDate);
    params.add(toDate);

    return sqlExecutor.selectList(sql, params);
  }

  @Override
  public Map<String, String> selectCountDistinctMonths(
      TenantIdentifier tenantId, LocalDate fromMonth, LocalDate toMonth) {

    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
            SELECT COUNT(DISTINCT DATE_TRUNC('month', stat_date)) as count
            FROM statistics_events
            WHERE tenant_id = ?::uuid
              AND stat_date >= ?::date
              AND stat_date < ?::date
            """;

    LocalDate fromMonthStart = fromMonth.withDayOfMonth(1);
    LocalDate toMonthEnd = toMonth.withDayOfMonth(1).plusMonths(1);

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(fromMonthStart);
    params.add(toMonthEnd);

    Map<String, String> result = sqlExecutor.selectOne(sql, params);
    return result != null ? result : Map.of();
  }
}
