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
import java.util.UUID;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class TenantYearlyStatisticsMysqlExecutor implements TenantYearlyStatisticsSqlExecutor {

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
                    ?,
                    ?,
                    ?,
                    JSON_OBJECT(?, ?),
                    NOW(),
                    NOW()
                )
                ON DUPLICATE KEY UPDATE
                    yearly_summary = JSON_SET(
                        COALESCE(yearly_summary, JSON_OBJECT()),
                        CONCAT('$.', ?),
                        COALESCE(JSON_EXTRACT(yearly_summary, CONCAT('$.', ?)), 0) + ?
                    ),
                    updated_at = NOW()
                """;

    List<Object> params = new ArrayList<>();
    // INSERT params
    params.add(UUID.randomUUID().toString());
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
