package org.idp.server.platform.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsData;
import org.idp.server.platform.statistics.TenantStatisticsDataIdentifier;

/**
 * Query repository for TenantStatisticsData
 *
 * <p>Provides read-only operations for daily tenant statistics retrieval.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Get last 7 days statistics
 * List<TenantStatisticsData> stats = repository.findByDateRange(
 *     tenantId,
 *     LocalDate.now().minusDays(7),
 *     LocalDate.now()
 * );
 *
 * // Get specific date
 * Optional<TenantStatisticsData> todayStats = repository.findByDate(
 *     tenantId,
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
   * @param tenantId tenant identifier
   * @param from start date (inclusive)
   * @param to end date (inclusive)
   * @return list of statistics (empty if not found)
   */
  List<TenantStatisticsData> findByDateRange(
      TenantIdentifier tenantId, LocalDate from, LocalDate to);

  /**
   * Find statistics for specific date
   *
   * @param tenantId tenant identifier
   * @param date target date
   * @return optional statistics (empty if not found)
   */
  Optional<TenantStatisticsData> findByDate(TenantIdentifier tenantId, LocalDate date);

  /**
   * Get statistics by ID
   *
   * @param id statistics identifier
   * @return statistics
   * @throws org.idp.server.platform.exception.ResourceNotFoundException if not found
   */
  TenantStatisticsData get(TenantStatisticsDataIdentifier id);

  /**
   * Find statistics by ID
   *
   * @param id statistics identifier
   * @return optional statistics (empty if not found)
   */
  Optional<TenantStatisticsData> find(TenantStatisticsDataIdentifier id);

  /**
   * Count statistics records in date range
   *
   * @param tenantId tenant identifier
   * @param from start date (inclusive)
   * @param to end date (inclusive)
   * @return total count
   */
  long countByDateRange(TenantIdentifier tenantId, LocalDate from, LocalDate to);

  /**
   * Find latest statistics
   *
   * @param tenantId tenant identifier
   * @return optional latest statistics (empty if no data exists)
   */
  Optional<TenantStatisticsData> findLatest(TenantIdentifier tenantId);

  /**
   * Check if statistics exists for date
   *
   * @param tenantId tenant identifier
   * @param date target date
   * @return true if exists
   */
  boolean exists(TenantIdentifier tenantId, LocalDate date);
}
