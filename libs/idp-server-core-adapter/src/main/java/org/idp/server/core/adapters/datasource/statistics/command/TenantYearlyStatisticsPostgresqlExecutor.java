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
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class TenantYearlyStatisticsPostgresqlExecutor implements TenantYearlyStatisticsSqlExecutor {

  @Override
  public void incrementYearlySummaryMetric(
      TenantIdentifier tenantId, LocalDate statYear, String metricName, int increment) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                INSERT INTO statistics_yearly (
                    id,
                    tenant_id,
                    stat_year,
                    yearly_summary,
                    created_at,
                    updated_at
                ) VALUES (
                    gen_random_uuid(),
                    ?::uuid,
                    ?,
                    jsonb_build_object(?::text, ?::int),
                    now(),
                    now()
                )
                ON CONFLICT (tenant_id, stat_year)
                DO UPDATE SET
                    yearly_summary = jsonb_set(
                        COALESCE(statistics_yearly.yearly_summary, '{}'::jsonb),
                        ARRAY[?::text],
                        to_jsonb(COALESCE((statistics_yearly.yearly_summary->>?::text)::int, 0) + ?::int)
                    ),
                    updated_at = now()
                """;

    List<Object> params = new ArrayList<>();
    // INSERT params
    params.add(tenantId.value());
    params.add(statYear);
    params.add(metricName);
    params.add(increment);
    // UPDATE params
    params.add(metricName);
    params.add(metricName);
    params.add(increment);

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void incrementYau(TenantIdentifier tenantId, LocalDate statYear, int increment) {
    incrementYearlySummaryMetric(tenantId, statYear, "yau", increment);
  }
}
