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
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantYearlyStatistics;
import org.idp.server.platform.statistics.TenantYearlyStatisticsIdentifier;

/**
 * Converts aggregated statistics data from SqlExecutor to TenantYearlyStatistics domain model.
 *
 * <p>The SqlExecutor already performs aggregation from statistics_events table, so this class only
 * handles the domain conversion.
 */
public class ModelConvertor {

  private static final JsonConverter jsonConverter = JsonConverter.defaultInstance();

  /**
   * Converts aggregated yearly statistics map to TenantYearlyStatistics domain object.
   *
   * @param values aggregated statistics from SqlExecutor
   * @return TenantYearlyStatistics domain object
   */
  @SuppressWarnings("unchecked")
  static TenantYearlyStatistics convert(Map<String, String> values) {
    String id = values.get("id");
    String tenantId = values.get("tenant_id");
    String statYear = values.get("stat_year");
    String yearlySummaryJson = values.get("yearly_summary");
    String createdAtStr = values.get("created_at");
    String updatedAtStr = values.get("updated_at");

    Map<String, Object> yearlySummary =
        yearlySummaryJson != null && !yearlySummaryJson.isEmpty()
            ? jsonConverter.read(yearlySummaryJson, Map.class)
            : new HashMap<>();

    Instant createdAt = parseTimestamp(createdAtStr);
    Instant updatedAt = parseTimestamp(updatedAtStr);

    return TenantYearlyStatistics.builder()
        .id(new TenantYearlyStatisticsIdentifier(id))
        .tenantId(new TenantIdentifier(tenantId))
        .statYear(statYear)
        .yearlySummary(yearlySummary)
        .createdAt(createdAt)
        .updatedAt(updatedAt)
        .build();
  }

  private static Instant parseTimestamp(String timestamp) {
    if (timestamp == null || timestamp.isEmpty()) {
      return Instant.now();
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
