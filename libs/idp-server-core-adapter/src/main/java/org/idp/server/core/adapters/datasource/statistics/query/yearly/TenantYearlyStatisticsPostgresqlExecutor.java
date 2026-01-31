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

package org.idp.server.core.adapters.datasource.statistics.query.yearly;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * PostgreSQL executor for yearly statistics queries.
 *
 * <p>Queries from statistics_events table and aggregates into TenantYearlyStatistics format.
 */
public class TenantYearlyStatisticsPostgresqlExecutor implements TenantYearlyStatisticsSqlExecutor {

  private final JsonConverter jsonConverter = JsonConverter.defaultInstance();

  @Override
  public Map<String, String> selectByYear(TenantIdentifier tenantId, LocalDate fiscalYearStart) {
    // fiscalYearStart is the actual fiscal year start date (e.g., 2025-04-01 for Japan FY)
    // Not necessarily January 1st
    LocalDate yearStart = fiscalYearStart;
    LocalDate nextYearStart = yearStart.plusYears(1);

    List<Map<String, String>> events = selectEventsForDateRange(tenantId, yearStart, nextYearStart);
    if (events.isEmpty()) {
      return Collections.emptyMap();
    }

    return aggregateToYearlyStatisticsMap(tenantId, yearStart, events);
  }

  @Override
  public Map<String, String> selectExists(TenantIdentifier tenantId, LocalDate fiscalYearStart) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    LocalDate yearStart = fiscalYearStart;
    LocalDate nextYearStart = yearStart.plusYears(1);

    String sql =
        """
            SELECT COUNT(*) as count
            FROM statistics_events
            WHERE tenant_id = ?::uuid
              AND stat_date >= ?::date
              AND stat_date < ?::date
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(yearStart);
    params.add(nextYearStart);

    return sqlExecutor.selectOne(sql, params);
  }

  private List<Map<String, String>> selectEventsForDateRange(
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

  private Map<String, String> aggregateToYearlyStatisticsMap(
      TenantIdentifier tenantId, LocalDate yearStart, List<Map<String, String>> events) {

    // Aggregate yearly summary (sum all counts by event_type)
    Map<String, Object> yearlySummary = new LinkedHashMap<>();

    Instant minCreatedAt = null;
    Instant maxUpdatedAt = null;

    for (Map<String, String> event : events) {
      String eventType = event.get("event_type");
      long count = Long.parseLong(event.get("count"));

      // Update yearly summary
      long currentTotal =
          yearlySummary.containsKey(eventType)
              ? ((Number) yearlySummary.get(eventType)).longValue()
              : 0L;
      yearlySummary.put(eventType, currentTotal + count);

      // Track timestamps
      String createdAtStr = event.get("created_at");
      String updatedAtStr = event.get("updated_at");
      if (createdAtStr != null) {
        Instant createdAt = parseTimestamp(createdAtStr);
        if (minCreatedAt == null || createdAt.isBefore(minCreatedAt)) {
          minCreatedAt = createdAt;
        }
      }
      if (updatedAtStr != null) {
        Instant updatedAt = parseTimestamp(updatedAtStr);
        if (maxUpdatedAt == null || updatedAt.isAfter(maxUpdatedAt)) {
          maxUpdatedAt = updatedAt;
        }
      }
    }

    // Build result map compatible with ModelConvertor
    Map<String, String> result = new LinkedHashMap<>();
    result.put("id", UUID.randomUUID().toString()); // Generated ID for compatibility
    result.put("tenant_id", tenantId.value());
    result.put("stat_year", yearStart.toString());
    result.put("yearly_summary", jsonConverter.write(yearlySummary));
    result.put(
        "created_at", minCreatedAt != null ? minCreatedAt.toString() : Instant.now().toString());
    result.put(
        "updated_at", maxUpdatedAt != null ? maxUpdatedAt.toString() : Instant.now().toString());

    return result;
  }

  private Instant parseTimestamp(String timestamp) {
    try {
      // Handle PostgreSQL timestamp format (2026-01-15 10:30:00.123456)
      if (timestamp.contains(" ") && !timestamp.contains("T")) {
        timestamp = timestamp.replace(" ", "T");
      }
      if (!timestamp.endsWith("Z") && !timestamp.contains("+")) {
        timestamp = timestamp + "Z";
      }
      return Instant.parse(timestamp);
    } catch (Exception e) {
      return Instant.now();
    }
  }
}
