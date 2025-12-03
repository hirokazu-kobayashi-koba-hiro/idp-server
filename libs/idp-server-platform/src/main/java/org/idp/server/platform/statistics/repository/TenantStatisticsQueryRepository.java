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

import java.util.List;
import java.util.Optional;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;
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
 * long totalCount = repository.countByMonthRange(tenant, "2025-01", "2025-03");
 *
 * // Get specific month
 * Optional<TenantStatistics> janStats = repository.findByMonth(tenant, "2025-01");
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
   * Find statistics for specific month
   *
   * @param tenant tenant
   * @param statMonth target month in YYYY-MM format (e.g., "2025-01")
   * @return optional statistics (empty if not found)
   */
  Optional<TenantStatistics> findByMonth(Tenant tenant, String statMonth);

  /**
   * Get statistics by ID
   *
   * @param tenant tenant
   * @param id statistics identifier
   * @return statistics
   * @throws org.idp.server.platform.exception.ResourceNotFoundException if not found
   */
  TenantStatistics get(Tenant tenant, TenantStatisticsIdentifier id);

  /**
   * Find statistics by ID
   *
   * @param tenant tenant
   * @param id statistics identifier
   * @return optional statistics (empty if not found)
   */
  Optional<TenantStatistics> find(Tenant tenant, TenantStatisticsIdentifier id);

  /**
   * Count statistics records in month range
   *
   * @param tenant tenant
   * @param fromMonth start month (inclusive, YYYY-MM format)
   * @param toMonth end month (inclusive, YYYY-MM format)
   * @return total count
   */
  long countByMonthRange(Tenant tenant, String fromMonth, String toMonth);

  /**
   * Find latest statistics
   *
   * @param tenant tenant
   * @return optional latest statistics (empty if no data exists)
   */
  Optional<TenantStatistics> findLatest(Tenant tenant);

  /**
   * Check if statistics exists for month
   *
   * @param tenant tenant
   * @param statMonth target month in YYYY-MM format
   * @return true if exists
   */
  boolean exists(Tenant tenant, String statMonth);
}
