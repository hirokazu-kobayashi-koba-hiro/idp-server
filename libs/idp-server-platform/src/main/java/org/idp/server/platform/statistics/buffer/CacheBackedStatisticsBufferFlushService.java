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

import java.util.Collections;
import java.util.List;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.statistics.StatisticsEventRecord;
import org.idp.server.platform.statistics.repository.StatisticsEventsCommandRepository;

/**
 * Service for flushing cache-backed statistics buffers to the database.
 *
 * <p>This service provides resilient flush operations with:
 *
 * <ul>
 *   <li>Atomic get-and-delete operations to prevent concurrent flush issues
 *   <li>Automatic recovery on DB write failure (restores values to cache)
 *   <li>Suitable for billing and mission-critical statistics
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Scheduled(fixedRate = 5000)
 * public void flushStatistics() {
 *     flushService.flushAll();
 * }
 * }</pre>
 */
public class CacheBackedStatisticsBufferFlushService implements StatisticsBufferFlusher {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(CacheBackedStatisticsBufferFlushService.class);

  private final CacheBackedStatisticsBufferManager bufferManager;
  private final StatisticsEventsCommandRepository repository;

  public CacheBackedStatisticsBufferFlushService(
      CacheBackedStatisticsBufferManager bufferManager,
      StatisticsEventsCommandRepository repository) {
    this.bufferManager = bufferManager;
    this.repository = repository;
  }

  /**
   * Flush all statistics from cache to the database.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Atomically gets and deletes all statistics keys from cache
   *   <li>Attempts to write to the database
   *   <li>If DB write fails, restores the values to cache
   * </ol>
   *
   * @return the total number of records flushed
   */
  @Override
  public int flushAll() {
    int totalFlushed = 0;

    try {
      List<StatisticsEventRecord> allRecords = bufferManager.drainAllFlat();
      if (allRecords.isEmpty()) {
        return 0;
      }

      try {
        repository.batchUpsert(allRecords);
        totalFlushed = allRecords.size();
        log.debug("Flushed {} statistics records from cache", totalFlushed);
      } catch (Exception dbException) {
        // DB write failed - restore all values to cache
        log.error(
            "DB write failed, restoring {} records to cache: {}", allRecords.size(), dbException);
        restoreRecords(allRecords);
        throw dbException;
      }

    } catch (Exception e) {
      log.error("Failed to flush statistics from cache: {}", e);
    }

    return totalFlushed;
  }

  /**
   * Flush statistics with individual record recovery.
   *
   * <p>Unlike {@link #flushAll()}, this method processes records one by one, restoring only the
   * records that fail to write. This is slower but provides finer-grained recovery.
   *
   * @return the total number of records successfully flushed
   */
  public int flushAllWithIndividualRecovery() {
    int totalFlushed = 0;

    try {
      List<StatisticsEventRecord> allRecords = bufferManager.drainAllFlat();

      for (StatisticsEventRecord record : allRecords) {
        try {
          repository.batchUpsert(Collections.singletonList(record));
          totalFlushed++;
        } catch (Exception e) {
          // Restore this specific record to cache
          log.error(
              "Failed to write record, restoring to cache: tenant={}, date={}, eventType={}",
              record.tenantId().value(),
              record.statDate(),
              record.eventType(),
              e);
          bufferManager.restore(
              record.tenantId(), record.statDate(), record.eventType(), record.count());
        }
      }

      if (totalFlushed > 0) {
        log.debug("Flushed {} statistics records from cache (individual mode)", totalFlushed);
      }

    } catch (Exception e) {
      log.error("Failed to flush statistics from cache: {}", e);
    }

    return totalFlushed;
  }

  /**
   * Get the buffer manager for direct access.
   *
   * @return the cache-backed buffer manager
   */
  public CacheBackedStatisticsBufferManager getBufferManager() {
    return bufferManager;
  }

  private void restoreRecords(List<StatisticsEventRecord> records) {
    for (StatisticsEventRecord record : records) {
      try {
        bufferManager.restore(
            record.tenantId(), record.statDate(), record.eventType(), record.count());
      } catch (Exception e) {
        log.error(
            "Failed to restore record to cache: tenant={}, date={}, eventType={}, count={}",
            record.tenantId().value(),
            record.statDate(),
            record.eventType(),
            record.count(),
            e);
      }
    }
  }
}
