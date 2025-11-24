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
import java.util.Optional;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;

/**
 * Query repository for TenantStatistics
 *
 * <p>Provides read-only operations for daily tenant statistics retrieval.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Get last 7 days statistics
 * TenantStatisticsQueries queries = new TenantStatisticsQueries(
 *     Map.of("from", "2025-01-01", "to", "2025-01-07")
 * );
 * List<TenantStatistics> stats = repository.findByDateRange(tenant, queries);
 *
 * // Get specific date
 * Optional<TenantStatistics> todayStats = repository.findByDate(
 *     tenant,
 *     LocalDate.now()
 * );
 * }</pre>
 *
 * @see TenantStatistics
 * @see TenantStatisticsCommandRepository
 */
public interface TenantStatisticsQueryRepository {

  /**
   * Find statistics by date range
   *
   * @param tenant tenant
   * @param queries query parameters containing from/to dates
   * @return list of statistics (empty if not found)
   */
  List<TenantStatistics> findByDateRange(Tenant tenant, TenantStatisticsQueries queries);

  /**
   * Find statistics for specific date
   *
   * @param tenant tenant
   * @param date target date
   * @return optional statistics (empty if not found)
   */
  Optional<TenantStatistics> findByDate(Tenant tenant, LocalDate date);

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
   * Count statistics records in date range
   *
   * @param tenant tenant
   * @param from start date (inclusive)
   * @param to end date (inclusive)
   * @return total count
   */
  long countByDateRange(Tenant tenant, LocalDate from, LocalDate to);

  /**
   * Find latest statistics
   *
   * @param tenant tenant
   * @return optional latest statistics (empty if no data exists)
   */
  Optional<TenantStatistics> findLatest(Tenant tenant);

  /**
   * Check if statistics exists for date
   *
   * @param tenant tenant
   * @param date target date
   * @return true if exists
   */
  boolean exists(Tenant tenant, LocalDate date);
}
