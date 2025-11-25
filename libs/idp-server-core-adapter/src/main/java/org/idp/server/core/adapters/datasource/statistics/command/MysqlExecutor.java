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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class MysqlExecutor implements TenantStatisticsSqlExecutor {

  @Override
  public void incrementMetric(
      TenantIdentifier tenantId, LocalDate date, String metricName, int increment) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                INSERT INTO tenant_statistics (
                    tenant_id,
                    stat_date,
                    metrics,
                    created_at,
                    updated_at
                ) VALUES (
                    ?,
                    ?,
                    JSON_OBJECT(?, ?),
                    ?,
                    ?
                )
                ON DUPLICATE KEY UPDATE
                    metrics = JSON_SET(
                        metrics,
                        CONCAT('$.', ?),
                        COALESCE(JSON_EXTRACT(metrics, CONCAT('$.', ?)), 0) + ?
                    ),
                    updated_at = ?
                """;

    Instant now = Instant.now();
    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(date);
    params.add(metricName);
    params.add(increment);
    params.add(now);
    params.add(now);
    params.add(metricName);
    params.add(metricName);
    params.add(increment);
    params.add(now);

    sqlExecutor.execute(sql, params);
  }
}
