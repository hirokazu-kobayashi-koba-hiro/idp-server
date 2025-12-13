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

package org.idp.server.platform.statistics;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Query parameters for tenant statistics
 *
 * <p>Supports month-based queries with pagination.
 *
 * <p>Query parameters:
 *
 * <ul>
 *   <li>from - Start month in YYYY-MM format (e.g., "2025-01")
 *   <li>to - End month in YYYY-MM format (e.g., "2025-03")
 *   <li>limit - Maximum number of records to return (default: 12)
 *   <li>offset - Number of records to skip (default: 0)
 * </ul>
 */
public class TenantStatisticsQueries {

  private static final int DEFAULT_LIMIT = 12;
  private static final int DEFAULT_OFFSET = 0;

  Map<String, String> values;

  public TenantStatisticsQueries() {
    this.values = new HashMap<>();
  }

  public TenantStatisticsQueries(Map<String, String> values) {
    this.values = Objects.requireNonNullElseGet(values, HashMap::new);
  }

  public boolean hasTenantId() {
    return values.containsKey("tenant_id");
  }

  public String tenantId() {
    return values.get("tenant_id");
  }

  public boolean hasFrom() {
    return values.containsKey("from");
  }

  public String from() {
    return values.get("from");
  }

  public LocalDate fromAsLocalDate() {
    String from = from();
    if (from == null || from.isEmpty()) {
      return null;
    }
    YearMonth yearMonth = YearMonth.parse(from, DateTimeFormatter.ofPattern("yyyy-MM"));
    return yearMonth.atDay(1);
  }

  public boolean hasTo() {
    return values.containsKey("to");
  }

  public String to() {
    return values.get("to");
  }

  public LocalDate toAsLocalDate() {
    String to = to();
    if (to == null || to.isEmpty()) {
      return null;
    }
    YearMonth yearMonth = YearMonth.parse(to, DateTimeFormatter.ofPattern("yyyy-MM"));
    return yearMonth.atDay(1);
  }

  public boolean hasLimit() {
    return values.containsKey("limit");
  }

  public int limit() {
    if (!hasLimit()) {
      return DEFAULT_LIMIT;
    }
    try {
      return Integer.parseInt(values.get("limit"));
    } catch (NumberFormatException e) {
      return DEFAULT_LIMIT;
    }
  }

  public boolean hasOffset() {
    return values.containsKey("offset");
  }

  public int offset() {
    if (!hasOffset()) {
      return DEFAULT_OFFSET;
    }
    try {
      return Integer.parseInt(values.get("offset"));
    } catch (NumberFormatException e) {
      return DEFAULT_OFFSET;
    }
  }

  public Map<String, String> toMap() {
    return values;
  }
}
