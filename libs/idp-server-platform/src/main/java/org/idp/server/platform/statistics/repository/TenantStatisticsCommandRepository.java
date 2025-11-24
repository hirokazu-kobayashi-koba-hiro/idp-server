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
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;

/**
 * Command repository for TenantStatistics
 *
 * <p>Provides write operations for daily tenant statistics.
 *
 * <p>Supports UPSERT: use {@link #save(TenantStatistics)} to insert or update based on unique
 * constraint (tenant_id, stat_date).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * TenantStatistics statistics = TenantStatistics.builder()
 *     .tenantId(tenantId)
 *     .statDate(LocalDate.now().minusDays(1))
 *     .addMetric("dau", 1250)
 *     .addMetric("login_success_rate", 97.5)
 *     .build();
 *
 * // UPSERT: insert if not exists, update if exists
 * repository.save(statistics);
 * }</pre>
 *
 * @see TenantStatistics
 * @see TenantStatisticsQueryRepository
 */
public interface TenantStatisticsCommandRepository {

  /**
   * Save statistics (UPSERT)
   *
   * <p>If data with same (tenant_id, stat_date) exists, it will be updated. Otherwise, new data
   * will be inserted.
   *
   * <p>Leverages PostgreSQL's ON CONFLICT DO UPDATE.
   *
   * @param statistics statistics to save
   */
  void save(TenantStatistics statistics);

  /**
   * Register new statistics (INSERT only)
   *
   * <p>Use when certain data doesn't exist yet.
   *
   * @param statistics statistics to register
   * @throws org.idp.server.platform.exception.DuplicateResourceException if already exists
   */
  void register(TenantStatistics statistics);

  /**
   * Update existing statistics
   *
   * @param statistics statistics to update
   * @throws org.idp.server.platform.exception.ResourceNotFoundException if not exists
   */
  void update(TenantStatistics statistics);

  /**
   * Delete statistics by ID
   *
   * @param id statistics identifier
   */
  void delete(TenantStatisticsIdentifier id);

  /**
   * Delete statistics by tenant and date
   *
   * @param tenantId tenant identifier
   * @param date target date
   */
  void deleteByDate(TenantIdentifier tenantId, LocalDate date);

  /**
   * Delete old statistics (data retention)
   *
   * <p>Example: Delete data older than 365 days
   *
   * <pre>{@code
   * repository.deleteOlderThan(LocalDate.now().minusDays(365));
   * }</pre>
   *
   * @param before delete data older than this date (exclusive)
   */
  void deleteOlderThan(LocalDate before);

  /**
   * Delete all statistics for a tenant
   *
   * @param tenantId tenant identifier
   */
  void deleteByTenantId(TenantIdentifier tenantId);

  /**
   * Increment a numeric metric value (real-time update)
   *
   * <p>Creates new record if (tenant_id, stat_date) doesn't exist. If metric doesn't exist,
   * initializes to increment value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Increment login_success_count by 1
   * repository.incrementMetric(tenantId, LocalDate.now(), "login_success_count", 1);
   * }</pre>
   *
   * @param tenantId tenant identifier
   * @param date statistics date
   * @param metricName metric name to increment
   * @param increment value to add (can be negative for decrement)
   */
  void incrementMetric(TenantIdentifier tenantId, LocalDate date, String metricName, int increment);
}
