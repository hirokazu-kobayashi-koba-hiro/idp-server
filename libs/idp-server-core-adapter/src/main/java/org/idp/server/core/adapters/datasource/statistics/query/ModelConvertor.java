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

package org.idp.server.core.adapters.datasource.statistics.query;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;

public class ModelConvertor {

  @SuppressWarnings("unchecked")
  static TenantStatistics convert(Map<String, String> values) {
    JsonConverter jsonConverter = JsonConverter.defaultInstance();

    String id = values.get("id");
    String tenantId = values.get("tenant_id");
    String statMonth = values.get("stat_month");
    String monthlySummary = values.get("monthly_summary");
    String dailyMetrics = values.get("daily_metrics");
    String createdAt = values.get("created_at");
    String updatedAt = values.get("updated_at");

    Map<String, Object> monthlySummaryMap =
        monthlySummary != null && !monthlySummary.isEmpty()
            ? jsonConverter.read(monthlySummary, Map.class)
            : new HashMap<>();

    Map<String, Map<String, Object>> dailyMetricsMap =
        dailyMetrics != null && !dailyMetrics.isEmpty()
            ? jsonConverter.read(dailyMetrics, Map.class)
            : new HashMap<>();

    // LocalDateTimeParser supports both PostgreSQL TIMESTAMP and ISO-8601 formats
    LocalDateTime createdAtLocalDateTime = LocalDateTimeParser.parse(createdAt);
    Instant createdAtInstant = createdAtLocalDateTime.atZone(ZoneOffset.UTC).toInstant();

    LocalDateTime updatedAtLocalDateTime = LocalDateTimeParser.parse(updatedAt);
    Instant updatedAtInstant = updatedAtLocalDateTime.atZone(ZoneOffset.UTC).toInstant();

    return TenantStatistics.builder()
        .id(new TenantStatisticsIdentifier(id))
        .tenantId(new TenantIdentifier(tenantId))
        .statMonth(statMonth)
        .monthlySummary(monthlySummaryMap)
        .dailyMetrics(dailyMetricsMap)
        .createdAt(createdAtInstant)
        .updatedAt(updatedAtInstant)
        .build();
  }
}
