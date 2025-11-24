package org.idp.server.platform.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantStatisticsData;
import org.idp.server.platform.statistics.TenantStatisticsDataIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;

/**
 * Query repository for TenantStatisticsData
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
 * List<TenantStatisticsData> stats = repository.findByDateRange(tenant, queries);
 *
 * // Get specific date
 * Optional<TenantStatisticsData> todayStats = repository.findByDate(
 *     tenant,
 *     LocalDate.now()
 * );
 * }</pre>
 *
 * @see TenantStatisticsData
 * @see TenantStatisticsDataCommandRepository
 */
public interface TenantStatisticsDataQueryRepository {

  /**
   * Find statistics by date range
   *
   * @param tenant tenant
   * @param queries query parameters containing from/to dates
   * @return list of statistics (empty if not found)
   */
  List<TenantStatisticsData> findByDateRange(Tenant tenant, TenantStatisticsQueries queries);

  /**
   * Find statistics for specific date
   *
   * @param tenant tenant
   * @param date target date
   * @return optional statistics (empty if not found)
   */
  Optional<TenantStatisticsData> findByDate(Tenant tenant, LocalDate date);

  /**
   * Get statistics by ID
   *
   * @param tenant tenant
   * @param id statistics identifier
   * @return statistics
   * @throws org.idp.server.platform.exception.ResourceNotFoundException if not found
   */
  TenantStatisticsData get(Tenant tenant, TenantStatisticsDataIdentifier id);

  /**
   * Find statistics by ID
   *
   * @param tenant tenant
   * @param id statistics identifier
   * @return optional statistics (empty if not found)
   */
  Optional<TenantStatisticsData> find(Tenant tenant, TenantStatisticsDataIdentifier id);

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
  Optional<TenantStatisticsData> findLatest(Tenant tenant);

  /**
   * Check if statistics exists for date
   *
   * @param tenant tenant
   * @param date target date
   * @return true if exists
   */
  boolean exists(Tenant tenant, LocalDate date);
}
