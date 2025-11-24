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
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;

public class MysqlExecutor implements TenantStatisticsSqlExecutor {

  @Override
  public void upsert(TenantStatistics data) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    JsonConverter jsonConverter = JsonConverter.defaultInstance();

    String sql =
        """
                INSERT INTO tenant_statistics (
                    id,
                    tenant_id,
                    stat_date,
                    metrics,
                    created_at,
                    updated_at
                ) VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                ON DUPLICATE KEY UPDATE
                    metrics = VALUES(metrics),
                    updated_at = VALUES(updated_at)
                """;

    List<Object> params = new ArrayList<>();
    params.add(
        data.id() != null
            ? data.id().toString()
            : new TenantStatisticsIdentifier(UUID.randomUUID()).value());
    params.add(data.tenantId().value());
    params.add(data.statDate());
    params.add(jsonConverter.write(data.metrics()));
    params.add(data.createdAt());
    params.add(data.updatedAt());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void insert(TenantStatistics data) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    JsonConverter jsonConverter = JsonConverter.defaultInstance();

    String sql =
        """
                INSERT INTO tenant_statistics (
                    id,
                    tenant_id,
                    stat_date,
                    metrics,
                    created_at,
                    updated_at
                ) VALUES (
                    ?,
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
            : new TenantStatisticsIdentifier(UUID.randomUUID()).value());
    params.add(data.tenantId().value());
    params.add(data.statDate());
    params.add(jsonConverter.write(data.metrics()));
    params.add(data.createdAt());
    params.add(data.updatedAt());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void update(TenantStatistics data) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    JsonConverter jsonConverter = JsonConverter.defaultInstance();

    String sql =
        """
                UPDATE tenant_statistics
                SET metrics = ?,
                    updated_at = ?
                WHERE id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(data.metrics()));
    params.add(data.updatedAt());
    params.add(data.id().value());

    sqlExecutor.execute(sql, params);
  }

  @Override
  public void delete(TenantStatisticsIdentifier id) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql = """
                DELETE FROM tenant_statistics WHERE id = ?
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
                DELETE FROM tenant_statistics
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
                DELETE FROM tenant_statistics
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
                DELETE FROM tenant_statistics WHERE tenant_id = ?
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
                INSERT INTO tenant_statistics (
                    id,
                    tenant_id,
                    stat_date,
                    metrics,
                    created_at,
                    updated_at
                ) VALUES (
                    ?,
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

    java.time.Instant now = java.time.Instant.now();
    List<Object> params = new ArrayList<>();
    params.add(new TenantStatisticsIdentifier(UUID.randomUUID()).value());
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
