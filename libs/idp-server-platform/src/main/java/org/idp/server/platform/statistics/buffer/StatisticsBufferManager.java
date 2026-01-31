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

package org.idp.server.platform.statistics.buffer;

import java.time.LocalDate;
import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;

/**
 * Interface for managing statistics event buffering.
 *
 * <p>Provides a common abstraction for both memory-based and cache-backed (e.g., Redis)
 * implementations. The appropriate implementation is selected based on the cache configuration.
 *
 * <p>Implementations:
 *
 * <ul>
 *   <li>{@link TenantStatisticsBufferManager} - Memory-based, for general statistics
 *   <li>{@link CacheBackedStatisticsBufferManager} - Cache-backed, for billing/mission-critical
 *       data
 * </ul>
 */
public interface StatisticsBufferManager {

  /**
   * Increment the count for an event type.
   *
   * @param tenantId the tenant identifier
   * @param date the date of the event
   * @param eventType the type of event
   */
  void increment(TenantIdentifier tenantId, LocalDate date, String eventType);

  /**
   * Add a count for an event type.
   *
   * @param tenantId the tenant identifier
   * @param date the date of the event
   * @param eventType the type of event
   * @param count the count to add
   */
  void add(TenantIdentifier tenantId, LocalDate date, String eventType, long count);

  /**
   * Drain all statistics and return as a flat list.
   *
   * @return list of all statistics event records
   */
  List<StatisticsEventRecord> drainAllFlat();

  /**
   * Drain a specific tenant's statistics.
   *
   * @param tenantId the tenant identifier
   * @return list of statistics event records for the tenant
   */
  List<StatisticsEventRecord> drainTenant(TenantIdentifier tenantId);
}
