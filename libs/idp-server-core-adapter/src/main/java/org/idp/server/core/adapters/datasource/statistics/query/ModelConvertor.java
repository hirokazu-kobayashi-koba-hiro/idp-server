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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsData;
import org.idp.server.platform.statistics.TenantStatisticsDataIdentifier;

public class ModelConvertor {

  static TenantStatisticsData convert(Map<String, String> values) {
    JsonConverter jsonConverter = JsonConverter.defaultInstance();

    String id = values.get("id");
    String tenantId = values.get("tenant_id");
    String statDate = values.get("stat_date");
    String metrics = values.get("metrics");
    String createdAt = values.get("created_at");

    Map<String, Object> metricsMap = jsonConverter.read(metrics, Map.class);

    // LocalDateTimeParser supports both PostgreSQL TIMESTAMP and ISO-8601 formats
    LocalDateTime localDateTime = LocalDateTimeParser.parse(createdAt);
    Instant createdAtInstant = localDateTime.atZone(ZoneOffset.UTC).toInstant();

    return TenantStatisticsData.builder()
        .id(new TenantStatisticsDataIdentifier(id))
        .tenantId(new TenantIdentifier(tenantId))
        .statDate(LocalDate.parse(statDate))
        .metrics(metricsMap)
        .createdAt(createdAtInstant)
        .build();
  }
}
