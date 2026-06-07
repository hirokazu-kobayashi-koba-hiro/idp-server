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

package org.idp.server.core.adapters.datasource.statistics.command.events;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;

/**
 * PostgreSQL implementation of statistics events upsert.
 *
 * <p>Writes target the {@code statistics_event_buckets} table (Issue #1443). Each write picks a
 * bucket id per writer via {@link StatisticsEventBuckets}, scattering UPSERTs across N rows per
 * logical key (tenant_id, stat_date, event_type) to eliminate hot-row lock contention.
 *
 * <p>Reads aggregate with {@code SUM(count) ... GROUP BY tenant_id, stat_date, event_type} so the
 * bucket dimension is transparent to consumers.
 */
public class PostgresqlExecutor implements StatisticsEventsSqlExecutor {

  private static final int BATCH_SIZE = 100;

  private static final String INCREMENT_SQL =
      """
      INSERT INTO statistics_event_buckets (tenant_id, stat_date, event_type, bucket_id, count, created_at, updated_at)
      VALUES (?::uuid, ?, ?, ?, 1, now(), now())
      ON CONFLICT (tenant_id, stat_date, event_type, bucket_id)
      DO UPDATE SET
          count = statistics_event_buckets.count + 1,
          updated_at = now()
      """;

  @Override
  public void increment(TenantIdentifier tenantId, LocalDate statDate, String eventType) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    sqlExecutor.execute(
        INCREMENT_SQL,
        List.of(tenantId.value(), statDate, eventType, StatisticsEventBuckets.pickBucketId()));
  }

  @Override
  public void batchUpsert(List<StatisticsEventRecord> records) {
    if (records == null || records.isEmpty()) {
      return;
    }

    SqlExecutor sqlExecutor = new SqlExecutor();

    // Process in batches to avoid excessively long SQL statements
    for (int i = 0; i < records.size(); i += BATCH_SIZE) {
      int endIndex = Math.min(i + BATCH_SIZE, records.size());
      List<StatisticsEventRecord> batch = records.subList(i, endIndex);
      executeBatch(sqlExecutor, batch);
    }
  }

  private void executeBatch(SqlExecutor sqlExecutor, List<StatisticsEventRecord> batch) {
    if (batch.isEmpty()) {
      return;
    }

    StringBuilder sql = new StringBuilder();
    sql.append(
        """
        INSERT INTO statistics_event_buckets (tenant_id, stat_date, event_type, bucket_id, count, created_at, updated_at)
        VALUES
        """);

    List<Object> params = new ArrayList<>();

    // Pick one bucket per batch call: keeps the increment to a single hot row per writer,
    // while the bucket changes between batches due to thread scheduling.
    int bucketId = StatisticsEventBuckets.pickBucketId();

    for (int i = 0; i < batch.size(); i++) {
      if (i > 0) {
        sql.append(",\n");
      }
      sql.append("(?::uuid, ?, ?, ?, ?, now(), now())");

      StatisticsEventRecord record = batch.get(i);
      params.add(record.tenantIdValue());
      params.add(record.statDate());
      params.add(record.eventType());
      params.add(bucketId);
      params.add(record.count());
    }

    sql.append(
        """

        ON CONFLICT (tenant_id, stat_date, event_type, bucket_id)
        DO UPDATE SET
            count = statistics_event_buckets.count + EXCLUDED.count,
            updated_at = now()
        """);

    sqlExecutor.execute(sql.toString(), params);
  }
}
