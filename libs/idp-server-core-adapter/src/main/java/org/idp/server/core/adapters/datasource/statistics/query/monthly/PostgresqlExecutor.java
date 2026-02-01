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

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;

/**
 * PostgreSQL executor for statistics queries.
 *
 * <p>Queries from statistics_events table and aggregates into TenantStatistics format.
 */
public class PostgresqlExecutor implements TenantStatisticsSqlExecutor {

  private final JsonConverter jsonConverter = JsonConverter.defaultInstance();

  @Override
  public Map<String, String> selectOne(TenantStatisticsIdentifier id) {
    // statistics_events doesn't have ID-based lookup
    // This method is kept for interface compatibility but returns empty
    return Collections.emptyMap();
  }

  @Override
  public Map<String, String> selectByMonth(TenantIdentifier tenantId, LocalDate statMonth) {
    LocalDate monthStart = statMonth.withDayOfMonth(1);
    LocalDate nextMonthStart = monthStart.plusMonths(1);

    List<Map<String, String>> events =
        selectEventsForDateRange(tenantId, monthStart, nextMonthStart);
    if (events.isEmpty()) {
      return Collections.emptyMap();
    }

    return aggregateToStatisticsMap(tenantId, monthStart, events);
  }

  @Override
  public List<Map<String, String>> selectByMonthRange(
      TenantIdentifier tenantId, TenantStatisticsQueries queries) {

    LocalDate fromMonth = queries.fromAsLocalDate().withDayOfMonth(1);
    LocalDate toMonth = queries.toAsLocalDate().withDayOfMonth(1);
    LocalDate toMonthEnd = toMonth.plusMonths(1);

    List<Map<String, String>> events = selectEventsForDateRange(tenantId, fromMonth, toMonthEnd);
    if (events.isEmpty()) {
      return Collections.emptyList();
    }

    // Group events by month
    Map<YearMonth, List<Map<String, String>>> eventsByMonth = new LinkedHashMap<>();
    for (Map<String, String> event : events) {
      LocalDate statDate = LocalDate.parse(event.get("stat_date"));
      YearMonth yearMonth = YearMonth.from(statDate);
      eventsByMonth.computeIfAbsent(yearMonth, k -> new ArrayList<>()).add(event);
    }

    // Convert each month's events to statistics format
    List<Map<String, String>> results = new ArrayList<>();
    List<YearMonth> sortedMonths = new ArrayList<>(eventsByMonth.keySet());
    sortedMonths.sort(Comparator.reverseOrder());

    int offset = queries.offset();
    int limit = queries.limit();
    int count = 0;

    for (YearMonth yearMonth : sortedMonths) {
      if (count < offset) {
        count++;
        continue;
      }
      if (results.size() >= limit) {
        break;
      }

      LocalDate monthStart = yearMonth.atDay(1);
      Map<String, String> statsMap =
          aggregateToStatisticsMap(tenantId, monthStart, eventsByMonth.get(yearMonth));
      results.add(statsMap);
      count++;
    }

    return results;
  }

  @Override
  public Map<String, String> selectCount(
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

    return sqlExecutor.selectOne(sql, params);
  }

  @Override
  public Map<String, String> selectLatest(TenantIdentifier tenantId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    // Find the latest month that has data
    String sql =
        """
                SELECT DATE_TRUNC('month', MAX(stat_date))::date as latest_month
                FROM statistics_events
                WHERE tenant_id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());

    Map<String, String> result = sqlExecutor.selectOne(sql, params);
    if (result == null || result.isEmpty() || result.get("latest_month") == null) {
      return Collections.emptyMap();
    }

    LocalDate latestMonth = LocalDate.parse(result.get("latest_month"));
    return selectByMonth(tenantId, latestMonth);
  }

  @Override
  public Map<String, String> selectExists(TenantIdentifier tenantId, LocalDate statMonth) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    LocalDate monthStart = statMonth.withDayOfMonth(1);
    LocalDate nextMonthStart = monthStart.plusMonths(1);

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
    params.add(monthStart);
    params.add(nextMonthStart);

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

  private Map<String, String> aggregateToStatisticsMap(
      TenantIdentifier tenantId, LocalDate monthStart, List<Map<String, String>> events) {

    // Aggregate monthly summary (sum all counts by event_type)
    Map<String, Object> monthlySummary = new LinkedHashMap<>();

    // Aggregate daily metrics (group by day)
    Map<String, Map<String, Object>> dailyMetrics = new LinkedHashMap<>();

    Instant minCreatedAt = null;
    Instant maxUpdatedAt = null;

    for (Map<String, String> event : events) {
      String eventType = event.get("event_type");
      long count = Long.parseLong(event.get("count"));
      LocalDate statDate = LocalDate.parse(event.get("stat_date"));
      String dayOfMonth = String.valueOf(statDate.getDayOfMonth());

      // Update monthly summary
      long currentTotal =
          monthlySummary.containsKey(eventType)
              ? ((Number) monthlySummary.get(eventType)).longValue()
              : 0L;
      monthlySummary.put(eventType, currentTotal + count);

      // Update daily metrics
      Map<String, Object> dayMetrics =
          dailyMetrics.computeIfAbsent(dayOfMonth, k -> new LinkedHashMap<>());
      dayMetrics.put(eventType, count);

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
    result.put("stat_month", monthStart.toString());
    result.put("monthly_summary", jsonConverter.write(monthlySummary));
    result.put("daily_metrics", jsonConverter.write(dailyMetrics));
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
