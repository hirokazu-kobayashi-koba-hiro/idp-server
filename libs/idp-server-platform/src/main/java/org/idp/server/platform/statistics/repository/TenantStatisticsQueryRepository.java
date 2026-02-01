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

package org.idp.server.platform.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsQueries;

/**
 * Query repository for TenantStatistics (monthly-based)
 *
 * <p>Provides read-only operations for monthly tenant statistics retrieval.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Get statistics for month range with pagination
 * TenantStatisticsQueries queries = new TenantStatisticsQueries(
 *     Map.of("from", "2025-01", "to", "2025-03", "limit", "10", "offset", "0")
 * );
 * List<TenantStatistics> stats = repository.findByMonthRange(tenant, queries);
 * long totalCount = repository.countByMonthRange(tenant, fromDate, toDate);
 * }</pre>
 *
 * @see TenantStatistics
 * @see TenantStatisticsCommandRepository
 */
public interface TenantStatisticsQueryRepository {

  /**
   * Find statistics by month range with pagination
   *
   * @param tenant tenant
   * @param queries query parameters containing from/to months, limit, offset
   * @return list of statistics (empty if not found)
   */
  List<TenantStatistics> findByMonthRange(Tenant tenant, TenantStatisticsQueries queries);

  /**
   * Count statistics records in month range
   *
   * @param tenant tenant
   * @param fromMonth start month as LocalDate (first day of month)
   * @param toMonth end month as LocalDate (first day of month)
   * @return total count
   */
  long countByMonthRange(Tenant tenant, LocalDate fromMonth, LocalDate toMonth);
}
