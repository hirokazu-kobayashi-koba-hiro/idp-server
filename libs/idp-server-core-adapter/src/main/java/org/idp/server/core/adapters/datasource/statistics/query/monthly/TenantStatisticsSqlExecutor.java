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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * SQL executor for statistics queries.
 *
 * <p>This interface is responsible only for executing SQL queries and returning raw data as
 * Map&lt;String, String&gt;. No aggregation or business logic should be implemented here.
 *
 * <p>Implementations are database-specific (PostgreSQL, MySQL).
 */
public interface TenantStatisticsSqlExecutor {

  /**
   * Select raw events for a date range.
   *
   * @param tenantId tenant identifier
   * @param fromDate start date (inclusive)
   * @param toDate end date (exclusive)
   * @return list of raw event rows as Map
   */
  List<Map<String, String>> selectEvents(
      TenantIdentifier tenantId, LocalDate fromDate, LocalDate toDate);

  /**
   * Count distinct months with data in a date range.
   *
   * @param tenantId tenant identifier
   * @param fromMonth start month (first day)
   * @param toMonth end month (first day)
   * @return result map containing "count" key
   */
  Map<String, String> selectCountDistinctMonths(
      TenantIdentifier tenantId, LocalDate fromMonth, LocalDate toMonth);

  /**
   * Find the latest date with data.
   *
   * @param tenantId tenant identifier
   * @return result map containing "latest_date" key, or empty map if no data
   */
  Map<String, String> selectLatestDate(TenantIdentifier tenantId);

  /**
   * Check if any events exist in a month.
   *
   * @param tenantId tenant identifier
   * @param monthStart first day of the month
   * @return result map containing "count" key
   */
  Map<String, String> selectExistsInMonth(TenantIdentifier tenantId, LocalDate monthStart);
}
