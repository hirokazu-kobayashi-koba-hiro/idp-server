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
import java.util.concurrent.ConcurrentHashMap;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;

/**
 * Manages statistics buffers for all tenants.
 *
 * <p>Provides tenant-isolated buffering with support for:
 *
 * <ul>
 *   <li>Per-tenant buffer management
 *   <li>Size-based flush triggers
 *   <li>Bulk drain operations
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * TenantStatisticsBufferManager manager = new TenantStatisticsBufferManager();
 *
 * // Accumulate events
 * manager.increment(tenantId, LocalDate.now(), "login_success");
 *
 * // Check if flush needed
 * if (manager.shouldFlush(tenantId, 1000)) {
 *     List<StatisticsEventRecord> records = manager.drainTenant(tenantId);
 *     repository.batchUpsert(records);
 * }
 * }</pre>
 */
public class TenantStatisticsBufferManager implements StatisticsBufferManager {

  private final ConcurrentHashMap<TenantIdentifier, StatisticsBuffer> tenantBuffers =
      new ConcurrentHashMap<>();

  /** Default maximum buffer size per tenant before triggering a flush */
  private static final int DEFAULT_MAX_BUFFER_SIZE = 1000;

  /**
   * Get or create a buffer for the specified tenant.
   *
   * @param tenantId the tenant identifier
   * @return the statistics buffer for the tenant
   */
  public StatisticsBuffer getBuffer(TenantIdentifier tenantId) {
    return tenantBuffers.computeIfAbsent(tenantId, StatisticsBuffer::new);
  }

  /**
   * Increment the count for an event type.
   *
   * @param tenantId the tenant identifier
   * @param date the date of the event
   * @param eventType the type of event
   */
  @Override
  public void increment(TenantIdentifier tenantId, LocalDate date, String eventType) {
    getBuffer(tenantId).increment(date, eventType);
  }

  /**
   * Add a count for an event type.
   *
   * @param tenantId the tenant identifier
   * @param date the date of the event
   * @param eventType the type of event
   * @param count the count to add
   */
  @Override
  public void add(TenantIdentifier tenantId, LocalDate date, String eventType, long count) {
    getBuffer(tenantId).add(date, eventType, count);
  }

  /**
   * Check if a tenant's buffer should be flushed based on size.
   *
   * @param tenantId the tenant identifier
   * @param maxSize the maximum buffer size
   * @return true if the buffer size exceeds the threshold
   */
  public boolean shouldFlush(TenantIdentifier tenantId, int maxSize) {
    StatisticsBuffer buffer = tenantBuffers.get(tenantId);
    return buffer != null && buffer.size() >= maxSize;
  }

  /**
   * Check if a tenant's buffer should be flushed using the default max size.
   *
   * @param tenantId the tenant identifier
   * @return true if the buffer size exceeds the default threshold
   */
  public boolean shouldFlush(TenantIdentifier tenantId) {
    return shouldFlush(tenantId, DEFAULT_MAX_BUFFER_SIZE);
  }

  /**
   * Get the buffer size for a tenant.
   *
   * @param tenantId the tenant identifier
   * @return the buffer size, or 0 if no buffer exists
   */
  public int getBufferSize(TenantIdentifier tenantId) {
    StatisticsBuffer buffer = tenantBuffers.get(tenantId);
    return buffer != null ? buffer.size() : 0;
  }

  /**
   * Drain a specific tenant's buffer and return accumulated records.
   *
   * @param tenantId the tenant identifier
   * @return list of statistics event records, empty if no buffer exists
   */
  @Override
  public List<StatisticsEventRecord> drainTenant(TenantIdentifier tenantId) {
    StatisticsBuffer buffer = tenantBuffers.get(tenantId);
    if (buffer != null) {
      return buffer.drainAndGetRecords();
    }
    return new ArrayList<>();
  }

  /**
   * Drain all tenant buffers and return accumulated records grouped by tenant.
   *
   * @return map of tenant ID to list of statistics event records
   */
  public Map<TenantIdentifier, List<StatisticsEventRecord>> drainAll() {
    Map<TenantIdentifier, List<StatisticsEventRecord>> result = new HashMap<>();

    tenantBuffers.forEach(
        (tenantId, buffer) -> {
          List<StatisticsEventRecord> records = buffer.drainAndGetRecords();
          if (!records.isEmpty()) {
            result.put(tenantId, records);
          }
        });

    return result;
  }

  /**
   * Drain all tenant buffers and return accumulated records as a flat list.
   *
   * @return list of all statistics event records from all tenants
   */
  @Override
  public List<StatisticsEventRecord> drainAllFlat() {
    List<StatisticsEventRecord> allRecords = new ArrayList<>();

    tenantBuffers.forEach(
        (tenantId, buffer) -> {
          allRecords.addAll(buffer.drainAndGetRecords());
        });

    return allRecords;
  }

  /**
   * Get all tenant IDs that have active buffers.
   *
   * @return list of tenant identifiers
   */
  public List<TenantIdentifier> getActiveTenants() {
    return new ArrayList<>(tenantBuffers.keySet());
  }

  /**
   * Remove a tenant's buffer entirely.
   *
   * @param tenantId the tenant identifier
   */
  public void removeTenant(TenantIdentifier tenantId) {
    tenantBuffers.remove(tenantId);
  }

  /** Clear all buffers. Use with caution - data will be lost. */
  public void clear() {
    tenantBuffers.clear();
  }
}
