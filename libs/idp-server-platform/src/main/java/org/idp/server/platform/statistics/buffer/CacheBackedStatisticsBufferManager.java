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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;

/**
 * Cache-backed statistics buffer manager for distributed environments.
 *
 * <p>This implementation uses {@link CacheStore} for atomic counter operations, providing:
 *
 * <ul>
 *   <li>Atomic increments across multiple application instances
 *   <li>No data loss during rolling deployments
 *   <li>Persistence through cache backend (e.g., Redis)
 *   <li>Suitable for billing and mission-critical statistics
 * </ul>
 *
 * <p>Key format: {@code statistics:{tenantId}:{date}:{eventType}}
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * CacheBackedStatisticsBufferManager manager = new CacheBackedStatisticsBufferManager(cacheStore);
 *
 * // Atomic increment from any instance
 * manager.increment(tenantId, LocalDate.now(), "login_success");
 *
 * // Flush to database
 * List<StatisticsEventRecord> records = manager.drainAllFlat();
 * repository.batchUpsert(records);
 * }</pre>
 */
public class CacheBackedStatisticsBufferManager implements StatisticsBufferManager {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(CacheBackedStatisticsBufferManager.class);

  private static final String KEY_PREFIX = "statistics:";
  private static final String KEY_SEPARATOR = ":";

  /** TTL for statistics keys: 7 days (enough time for flushing even with delays) */
  private static final int DEFAULT_TTL_SECONDS = 7 * 24 * 60 * 60;

  private final CacheStore cacheStore;
  private final int ttlSeconds;

  public CacheBackedStatisticsBufferManager(CacheStore cacheStore) {
    this(cacheStore, DEFAULT_TTL_SECONDS);
  }

  public CacheBackedStatisticsBufferManager(CacheStore cacheStore, int ttlSeconds) {
    this.cacheStore = cacheStore;
    this.ttlSeconds = ttlSeconds;
  }

  /**
   * Increment the count for an event type atomically.
   *
   * @param tenantId the tenant identifier
   * @param date the date of the event
   * @param eventType the type of event
   */
  @Override
  public void increment(TenantIdentifier tenantId, LocalDate date, String eventType) {
    String key = buildKey(tenantId, date, eventType);
    cacheStore.incrementBy(key, 1, ttlSeconds);
  }

  /**
   * Add a count for an event type atomically.
   *
   * @param tenantId the tenant identifier
   * @param date the date of the event
   * @param eventType the type of event
   * @param count the count to add
   */
  @Override
  public void add(TenantIdentifier tenantId, LocalDate date, String eventType, long count) {
    String key = buildKey(tenantId, date, eventType);
    cacheStore.incrementBy(key, count, ttlSeconds);
  }

  /**
   * Drain all statistics and return as a flat list.
   *
   * <p>This method atomically gets and deletes each key to prevent data loss during concurrent
   * flush operations.
   *
   * @return list of all statistics event records
   */
  @Override
  public List<StatisticsEventRecord> drainAllFlat() {
    List<StatisticsEventRecord> allRecords = new ArrayList<>();

    Set<String> keys = cacheStore.keys(KEY_PREFIX + "*");
    for (String key : keys) {
      long count = cacheStore.getAndDelete(key);
      if (count > 0) {
        StatisticsEventRecord record = parseKeyToRecord(key, count);
        if (record != null) {
          allRecords.add(record);
        }
      }
    }

    return allRecords;
  }

  /**
   * Drain all statistics grouped by tenant.
   *
   * @return map of tenant ID to list of statistics event records
   */
  public Map<TenantIdentifier, List<StatisticsEventRecord>> drainAll() {
    Map<TenantIdentifier, List<StatisticsEventRecord>> result = new HashMap<>();

    List<StatisticsEventRecord> allRecords = drainAllFlat();
    for (StatisticsEventRecord record : allRecords) {
      result.computeIfAbsent(record.tenantId(), k -> new ArrayList<>()).add(record);
    }

    return result;
  }

  /**
   * Drain a specific tenant's statistics.
   *
   * @param tenantId the tenant identifier
   * @return list of statistics event records for the tenant
   */
  @Override
  public List<StatisticsEventRecord> drainTenant(TenantIdentifier tenantId) {
    List<StatisticsEventRecord> records = new ArrayList<>();

    String pattern = KEY_PREFIX + tenantId.value() + KEY_SEPARATOR + "*";
    Set<String> keys = cacheStore.keys(pattern);

    for (String key : keys) {
      long count = cacheStore.getAndDelete(key);
      if (count > 0) {
        StatisticsEventRecord record = parseKeyToRecord(key, count);
        if (record != null) {
          records.add(record);
        }
      }
    }

    return records;
  }

  /**
   * Get the current count for a specific event without draining.
   *
   * @param tenantId the tenant identifier
   * @param date the date
   * @param eventType the event type
   * @return the current count, or 0 if not found
   */
  public long getCount(TenantIdentifier tenantId, LocalDate date, String eventType) {
    String key = buildKey(tenantId, date, eventType);
    // Use incrementBy with 0 to read current value without modification
    return cacheStore.incrementBy(key, 0);
  }

  /**
   * Check if there are any buffered statistics.
   *
   * @return true if there are buffered statistics
   */
  public boolean hasBufferedData() {
    Set<String> keys = cacheStore.keys(KEY_PREFIX + "*");
    return !keys.isEmpty();
  }

  /**
   * Restore a value to cache (used for recovery when DB write fails).
   *
   * @param tenantId the tenant identifier
   * @param date the date
   * @param eventType the event type
   * @param count the count to restore
   */
  public void restore(TenantIdentifier tenantId, LocalDate date, String eventType, long count) {
    String key = buildKey(tenantId, date, eventType);
    cacheStore.incrementBy(key, count, ttlSeconds);
    log.debug("Restored {} to cache key: {}", count, key);
  }

  private String buildKey(TenantIdentifier tenantId, LocalDate date, String eventType) {
    return KEY_PREFIX
        + tenantId.value()
        + KEY_SEPARATOR
        + date.toString()
        + KEY_SEPARATOR
        + eventType;
  }

  private StatisticsEventRecord parseKeyToRecord(String key, long count) {
    // Key format: statistics:{tenantId}:{date}:{eventType}
    if (!key.startsWith(KEY_PREFIX)) {
      log.warn("Invalid statistics key format: {}", key);
      return null;
    }

    String remaining = key.substring(KEY_PREFIX.length());
    String[] parts = remaining.split(KEY_SEPARATOR, 3);

    if (parts.length != 3) {
      log.warn("Invalid statistics key format: {}", key);
      return null;
    }

    try {
      TenantIdentifier tenantId = new TenantIdentifier(parts[0]);
      LocalDate date = LocalDate.parse(parts[1]);
      String eventType = parts[2];
      return new StatisticsEventRecord(tenantId, date, eventType, count);
    } catch (Exception e) {
      log.warn("Failed to parse statistics key: {}", key, e);
      return null;
    }
  }
}
