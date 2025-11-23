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
import java.util.UUID;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsData;
import org.idp.server.platform.statistics.TenantStatisticsDataIdentifier;

public class MysqlExecutor implements TenantStatisticsDataSqlExecutor {

  @Override
  public void upsert(TenantStatisticsData data) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    JsonConverter jsonConverter = JsonConverter.defaultInstance();

    String sql =
        """
                INSERT INTO tenant_statistics_data (
                    id,
                    tenant_id,
                    stat_date,
                    metrics,
                    created_at
                ) VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                ON DUPLICATE KEY UPDATE
                    metrics = VALUES(metrics),
                    created_at = VALUES(created_at)
                """;

    List<Object> params = new ArrayList<>();
    params.add(
        data.id() != null
            ? data.id().toString()
            : new TenantStatisticsDataIdentifier(UUID.randomUUID()).value());
    params.add(data.tenantId().value());
    params.add(data.statDate());
    params.add(jsonConverter.write(data.metrics()));
    params.add(data.createdAt());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void insert(TenantStatisticsData data) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    JsonConverter jsonConverter = JsonConverter.defaultInstance();

    String sql =
        """
                INSERT INTO tenant_statistics_data (
                    id,
                    tenant_id,
                    stat_date,
                    metrics,
                    created_at
                ) VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """;

    List<Object> params = new ArrayList<>();
    params.add(
        data.id() != null
            ? data.id().toString()
            : new TenantStatisticsDataIdentifier(UUID.randomUUID()).value());
    params.add(data.tenantId().value());
    params.add(data.statDate());
    params.add(jsonConverter.write(data.metrics()));
    params.add(data.createdAt());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void update(TenantStatisticsData data) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    JsonConverter jsonConverter = JsonConverter.defaultInstance();

    String sql =
        """
                UPDATE tenant_statistics_data
                SET metrics = ?,
                    created_at = ?
                WHERE id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(data.metrics()));
    params.add(data.createdAt());
    params.add(data.id().value());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void delete(TenantStatisticsDataIdentifier id) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                DELETE FROM tenant_statistics_data WHERE id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(id.value());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void deleteByDate(TenantIdentifier tenantId, LocalDate date) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                DELETE FROM tenant_statistics_data
                WHERE tenant_id = ? AND stat_date = ?
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
                DELETE FROM tenant_statistics_data
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
                DELETE FROM tenant_statistics_data WHERE tenant_id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void incrementMetric(
      TenantIdentifier tenantId, LocalDate date, String metricName, int increment) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
                INSERT INTO tenant_statistics_data (
                    id,
                    tenant_id,
                    stat_date,
                    metrics,
                    created_at
                ) VALUES (
                    ?,
                    ?,
                    ?,
                    JSON_OBJECT(?, ?),
                    ?
                )
                ON DUPLICATE KEY UPDATE
                    metrics = JSON_SET(
                        metrics,
                        CONCAT('$.', ?),
                        COALESCE(JSON_EXTRACT(metrics, CONCAT('$.', ?)), 0) + ?
                    )
                """;

    List<Object> params = new ArrayList<>();
    params.add(new TenantStatisticsDataIdentifier(UUID.randomUUID()).value());
    params.add(tenantId.value());
    params.add(date);
    params.add(metricName);
    params.add(increment);
    params.add(java.time.Instant.now());
    params.add(metricName);
    params.add(metricName);
    params.add(increment);

    sqlExecutor.execute(sql, params);
  }
}
