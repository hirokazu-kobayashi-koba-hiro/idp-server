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

public class PostgresqlExecutor implements TenantStatisticsSqlExecutor {

  @Override
  public void incrementDailyMetric(
      TenantIdentifier tenantId,
      LocalDate statMonth,
      String day,
      String metricName,
      int increment) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Ensure day object exists, then increment the metric within it
    String sql =
        """
                INSERT INTO statistics_monthly (
                    id,
                    tenant_id,
                    stat_month,
                    monthly_summary,
                    daily_metrics,
                    created_at,
                    updated_at
                ) VALUES (
                    gen_random_uuid(),
                    ?::uuid,
                    ?,
                    '{}'::jsonb,
                    jsonb_build_object(?::text, jsonb_build_object(?::text, ?::int)),
                    now(),
                    now()
                )
                ON CONFLICT (tenant_id, stat_month)
                DO UPDATE SET
                    daily_metrics = jsonb_set(
                        COALESCE(statistics_monthly.daily_metrics, '{}'::jsonb),
                        ARRAY[?::text],
                        jsonb_set(
                            COALESCE(statistics_monthly.daily_metrics->?::text, '{}'::jsonb),
                            ARRAY[?::text],
                            to_jsonb(COALESCE((statistics_monthly.daily_metrics->?::text->>?::text)::int, 0) + ?::int)
                        )
                    ),
                    updated_at = now()
                """;

    List<Object> params = new ArrayList<>();
    // INSERT params
    params.add(tenantId.value());
    params.add(statMonth);
    params.add(day);
    params.add(metricName);
    params.add(increment);
    // UPDATE params
    params.add(day); // ARRAY[?::text] for outer jsonb_set
    params.add(day); // daily_metrics->?::text for getting day object
    params.add(metricName); // ARRAY[?::text] for inner jsonb_set
    params.add(day); // daily_metrics->?::text for getting current value
    params.add(metricName); // ->>?::text for getting metric value
    params.add(increment);

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void incrementMonthlySummaryMetric(
      TenantIdentifier tenantId, LocalDate statMonth, String metricName, int increment) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                INSERT INTO statistics_monthly (
                    id,
                    tenant_id,
                    stat_month,
                    monthly_summary,
                    daily_metrics,
                    created_at,
                    updated_at
                ) VALUES (
                    gen_random_uuid(),
                    ?::uuid,
                    ?,
                    jsonb_build_object(?::text, ?::int),
                    '{}'::jsonb,
                    now(),
                    now()
                )
                ON CONFLICT (tenant_id, stat_month)
                DO UPDATE SET
                    monthly_summary = jsonb_set(
                        COALESCE(statistics_monthly.monthly_summary, '{}'::jsonb),
                        ARRAY[?::text],
                        to_jsonb(COALESCE((statistics_monthly.monthly_summary->>?::text)::int, 0) + ?::int)
                    ),
                    updated_at = now()
                """;

    List<Object> params = new ArrayList<>();
    // INSERT params
    params.add(tenantId.value());
    params.add(statMonth);
    params.add(metricName);
    params.add(increment);
    // UPDATE params
    params.add(metricName);
    params.add(metricName);
    params.add(increment);

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void incrementMauWithDailyCumulative(
      TenantIdentifier tenantId, LocalDate statMonth, String day, int increment) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Atomically increment monthly_summary.mau and set daily_metrics[day].mau to cumulative value
    String sql =
        """
                INSERT INTO statistics_monthly (
                    id,
                    tenant_id,
                    stat_month,
                    monthly_summary,
                    daily_metrics,
                    created_at,
                    updated_at
                ) VALUES (
                    gen_random_uuid(),
                    ?::uuid,
                    ?,
                    jsonb_build_object('mau', ?::int),
                    jsonb_build_object(?::text, jsonb_build_object('mau', ?::int)),
                    now(),
                    now()
                )
                ON CONFLICT (tenant_id, stat_month)
                DO UPDATE SET
                    monthly_summary = jsonb_set(
                        COALESCE(statistics_monthly.monthly_summary, '{}'::jsonb),
                        '{mau}',
                        to_jsonb(COALESCE((statistics_monthly.monthly_summary->>'mau')::int, 0) + ?::int)
                    ),
                    daily_metrics = jsonb_set(
                        jsonb_set(
                            COALESCE(statistics_monthly.daily_metrics, '{}'::jsonb),
                            ARRAY[?::text],
                            COALESCE(statistics_monthly.daily_metrics->?::text, '{}'::jsonb)
                        ),
                        ARRAY[?::text, 'mau'],
                        to_jsonb(COALESCE((statistics_monthly.monthly_summary->>'mau')::int, 0) + ?::int)
                    ),
                    updated_at = now()
                """;

    List<Object> params = new ArrayList<>();
    // INSERT params
    params.add(tenantId.value());
    params.add(statMonth);
    params.add(increment); // mau initial value
    params.add(day); // day key for daily_metrics
    params.add(increment); // mau initial value in daily
    // UPDATE params
    params.add(increment); // increment for monthly_summary.mau
    params.add(day); // ARRAY[?::text] for ensuring day object exists
    params.add(day); // daily_metrics->?::text for getting day object
    params.add(day); // ARRAY[?::text, 'mau'] for setting mau
    params.add(increment); // increment for daily_metrics[day].mau

    sqlExecutor.execute(sql, params);
  }
}
