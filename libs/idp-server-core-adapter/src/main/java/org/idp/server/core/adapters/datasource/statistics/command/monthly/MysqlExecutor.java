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

public class MysqlExecutor implements TenantStatisticsSqlExecutor {

  @Override
  public void incrementDailyMetric(
      TenantIdentifier tenantId,
      LocalDate statMonth,
      String day,
      String metricName,
      int increment) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // MySQL JSON_SET with nested path
    // Note: Use quoted keys like $."2025-12-17" for keys containing hyphens
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
                    UUID(),
                    ?,
                    ?,
                    JSON_OBJECT(),
                    JSON_OBJECT(?, JSON_OBJECT(?, ?)),
                    NOW(),
                    NOW()
                )
                ON DUPLICATE KEY UPDATE
                    daily_metrics = JSON_SET(
                        COALESCE(daily_metrics, JSON_OBJECT()),
                        CONCAT('$."', ?, '"'),
                        JSON_SET(
                            COALESCE(JSON_EXTRACT(daily_metrics, CONCAT('$."', ?, '"')), JSON_OBJECT()),
                            CONCAT('$."', ?, '"'),
                            COALESCE(JSON_EXTRACT(daily_metrics, CONCAT('$."', ?, '"."', ?, '"')), 0) + ?
                        )
                    ),
                    updated_at = NOW()
                """;

    List<Object> params = new ArrayList<>();
    // INSERT params
    params.add(tenantId.value());
    params.add(statMonth);
    params.add(day);
    params.add(metricName);
    params.add(increment);
    // UPDATE params
    params.add(day);
    params.add(day);
    params.add(metricName);
    params.add(day);
    params.add(metricName);
    params.add(increment);

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void incrementMonthlySummaryMetric(
      TenantIdentifier tenantId, LocalDate statMonth, String metricName, int increment) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Note: Use quoted keys like $."metric-name" for keys containing hyphens
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
                    UUID(),
                    ?,
                    ?,
                    JSON_OBJECT(?, ?),
                    JSON_OBJECT(),
                    NOW(),
                    NOW()
                )
                ON DUPLICATE KEY UPDATE
                    monthly_summary = JSON_SET(
                        COALESCE(monthly_summary, JSON_OBJECT()),
                        CONCAT('$."', ?, '"'),
                        COALESCE(JSON_EXTRACT(monthly_summary, CONCAT('$."', ?, '"')), 0) + ?
                    ),
                    updated_at = NOW()
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
    // Note: Use quoted keys like $."2025-12-17" for keys containing hyphens
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
                    UUID(),
                    ?,
                    ?,
                    JSON_OBJECT('mau', ?),
                    JSON_OBJECT(?, JSON_OBJECT('mau', ?)),
                    NOW(),
                    NOW()
                )
                ON DUPLICATE KEY UPDATE
                    monthly_summary = JSON_SET(
                        COALESCE(monthly_summary, JSON_OBJECT()),
                        '$.mau',
                        COALESCE(JSON_EXTRACT(monthly_summary, '$.mau'), 0) + ?
                    ),
                    daily_metrics = JSON_SET(
                        JSON_SET(
                            COALESCE(daily_metrics, JSON_OBJECT()),
                            CONCAT('$."', ?, '"'),
                            COALESCE(JSON_EXTRACT(daily_metrics, CONCAT('$."', ?, '"')), JSON_OBJECT())
                        ),
                        CONCAT('$."', ?, '".mau'),
                        COALESCE(JSON_EXTRACT(monthly_summary, '$.mau'), 0) + ?
                    ),
                    updated_at = NOW()
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
    params.add(day); // for ensuring day object exists
    params.add(day); // for getting day object
    params.add(day); // for setting mau path
    params.add(increment); // increment for daily_metrics[day].mau

    sqlExecutor.execute(sql, params);
  }
}
