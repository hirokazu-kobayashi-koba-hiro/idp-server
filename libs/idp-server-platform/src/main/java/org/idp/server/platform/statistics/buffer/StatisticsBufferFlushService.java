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

import java.util.List;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;
import org.idp.server.platform.statistics.repository.StatisticsEventsCommandRepository;

/**
 * Service for flushing statistics buffers to the database.
 *
 * <p>Provides both on-demand and periodic flush capabilities:
 *
 * <ul>
 *   <li>Flush specific tenant when buffer size threshold exceeded
 *   <li>Flush all tenants periodically
 * </ul>
 *
 * <p>Example usage with scheduler:
 *
 * <pre>{@code
 * @Scheduled(fixedRate = 5000)
 * public void flushStatistics() {
 *     flushService.flushAll();
 * }
 * }</pre>
 */
public class StatisticsBufferFlushService implements StatisticsBufferFlusher {

  private final TenantStatisticsBufferManager bufferManager;
  private final StatisticsEventsCommandRepository repository;
  private final LoggerWrapper log = LoggerWrapper.getLogger(StatisticsBufferFlushService.class);

  /** Maximum buffer size per tenant before triggering flush */
  private final int maxBufferSize;

  /** Default max buffer size */
  private static final int DEFAULT_MAX_BUFFER_SIZE = 1000;

  public StatisticsBufferFlushService(
      TenantStatisticsBufferManager bufferManager, StatisticsEventsCommandRepository repository) {
    this(bufferManager, repository, DEFAULT_MAX_BUFFER_SIZE);
  }

  public StatisticsBufferFlushService(
      TenantStatisticsBufferManager bufferManager,
      StatisticsEventsCommandRepository repository,
      int maxBufferSize) {
    this.bufferManager = bufferManager;
    this.repository = repository;
    this.maxBufferSize = maxBufferSize;
  }

  /**
   * Flush a specific tenant's buffer if it exceeds the size threshold.
   *
   * @param tenantId the tenant to potentially flush
   * @return true if the buffer was flushed
   */
  public boolean flushIfNeeded(TenantIdentifier tenantId) {
    if (bufferManager.shouldFlush(tenantId, maxBufferSize)) {
      return flushTenant(tenantId);
    }
    return false;
  }

  /**
   * Flush a specific tenant's buffer.
   *
   * @param tenantId the tenant to flush
   * @return true if records were flushed
   */
  public boolean flushTenant(TenantIdentifier tenantId) {
    try {
      List<StatisticsEventRecord> records = bufferManager.drainTenant(tenantId);
      if (!records.isEmpty()) {
        repository.batchUpsert(records);
        log.debug("Flushed {} statistics records for tenant {}", records.size(), tenantId.value());
        return true;
      }
    } catch (Exception e) {
      log.error("Failed to flush statistics buffer for tenant {}: {}", tenantId.value(), e);
    }
    return false;
  }

  /**
   * Flush all tenant buffers.
   *
   * @return the total number of records flushed
   */
  @Override
  public int flushAll() {
    int totalFlushed = 0;

    try {
      List<StatisticsEventRecord> allRecords = bufferManager.drainAllFlat();
      if (!allRecords.isEmpty()) {
        repository.batchUpsert(allRecords);
        totalFlushed = allRecords.size();
        log.debug("Flushed {} total statistics records", totalFlushed);
      }
    } catch (Exception e) {
      log.error("Failed to flush all statistics buffers: {}", e);
    }

    return totalFlushed;
  }

  /**
   * Get the buffer manager for direct access.
   *
   * @return the buffer manager
   */
  public TenantStatisticsBufferManager getBufferManager() {
    return bufferManager;
  }
}
