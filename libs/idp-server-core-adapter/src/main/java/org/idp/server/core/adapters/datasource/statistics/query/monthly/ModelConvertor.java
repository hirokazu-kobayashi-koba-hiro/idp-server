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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;

/**
 * Converts raw statistics data from SqlExecutor to TenantStatistics domain model.
 *
 * <p>This class handles both aggregation and domain conversion:
 *
 * <ul>
 *   <li>Aggregating raw event rows by month
 *   <li>Calculating monthly summaries and daily metrics
 *   <li>Converting to TenantStatistics domain object
 * </ul>
 */
public class ModelConvertor {

  /**
   * Converts raw events for a single month to TenantStatistics.
   *
   * @param tenantId the tenant identifier
   * @param monthStart the first day of the month
   * @param rawEvents list of raw events from SqlExecutor
   * @return TenantStatistics domain object
   */
  public static TenantStatistics convert(
      TenantIdentifier tenantId, LocalDate monthStart, List<Map<String, String>> rawEvents) {

    Map<String, Object> monthlySummary = new HashMap<>();
    Map<String, Map<String, Object>> dailyMetrics = new HashMap<>();
    Instant minCreatedAt = null;
    Instant maxUpdatedAt = null;

    for (Map<String, String> row : rawEvents) {
      LocalDate statDate = LocalDate.parse(row.get("stat_date"));
      String eventType = row.get("event_type");
      long count = Long.parseLong(row.get("count"));
      Instant createdAt = parseTimestamp(row.get("created_at"));
      Instant updatedAt = parseTimestamp(row.get("updated_at"));

      String dayKey = String.valueOf(statDate.getDayOfMonth());

      // Update monthly summary
      long currentCount =
          monthlySummary.containsKey(eventType)
              ? ((Number) monthlySummary.get(eventType)).longValue()
              : 0L;
      monthlySummary.put(eventType, currentCount + count);

      // Update daily metrics
      dailyMetrics.computeIfAbsent(dayKey, k -> new HashMap<>()).put(eventType, count);

      // Track timestamps
      if (createdAt != null && (minCreatedAt == null || createdAt.isBefore(minCreatedAt))) {
        minCreatedAt = createdAt;
      }
      if (updatedAt != null && (maxUpdatedAt == null || updatedAt.isAfter(maxUpdatedAt))) {
        maxUpdatedAt = updatedAt;
      }
    }

    String deterministicId = generateDeterministicId(tenantId, monthStart);

    return TenantStatistics.builder()
        .id(new TenantStatisticsIdentifier(deterministicId))
        .tenantId(tenantId)
        .statMonth(monthStart.toString())
        .monthlySummary(monthlySummary)
        .dailyMetrics(dailyMetrics)
        .createdAt(minCreatedAt != null ? minCreatedAt : Instant.now())
        .updatedAt(maxUpdatedAt != null ? maxUpdatedAt : Instant.now())
        .build();
  }

  /**
   * Generates a deterministic UUID based on tenant ID and month.
   *
   * <p>This ensures the same tenant and month always produce the same ID, which is important for
   * caching and idempotent operations.
   *
   * @param tenantId the tenant identifier
   * @param monthStart the first day of the month
   * @return deterministic UUID string
   */
  private static String generateDeterministicId(TenantIdentifier tenantId, LocalDate monthStart) {
    String seed = tenantId.value() + "-" + monthStart.toString();
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
  }

  /**
   * Converts raw events spanning multiple months to list of TenantStatistics.
   *
   * @param tenantId the tenant identifier
   * @param rawEvents list of raw events from SqlExecutor
   * @return list of TenantStatistics, sorted by month descending
   */
  public static List<TenantStatistics> convertByMonth(
      TenantIdentifier tenantId, List<Map<String, String>> rawEvents) {

    // Group events by month
    Map<YearMonth, List<Map<String, String>>> eventsByMonth = new LinkedHashMap<>();
    for (Map<String, String> row : rawEvents) {
      LocalDate statDate = LocalDate.parse(row.get("stat_date"));
      YearMonth yearMonth = YearMonth.from(statDate);
      eventsByMonth.computeIfAbsent(yearMonth, k -> new ArrayList<>()).add(row);
    }

    // Sort months descending and convert each
    List<YearMonth> sortedMonths = new ArrayList<>(eventsByMonth.keySet());
    sortedMonths.sort(Comparator.reverseOrder());

    List<TenantStatistics> result = new ArrayList<>();
    for (YearMonth yearMonth : sortedMonths) {
      LocalDate monthStart = yearMonth.atDay(1);
      TenantStatistics stats = convert(tenantId, monthStart, eventsByMonth.get(yearMonth));
      result.add(stats);
    }

    return result;
  }

  private static Instant parseTimestamp(String timestamp) {
    if (timestamp == null) {
      return null;
    }
    try {
      // Handle both PostgreSQL and MySQL timestamp formats
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
