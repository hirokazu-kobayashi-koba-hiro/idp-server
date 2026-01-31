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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;

/**
 * Thread-safe buffer for accumulating statistics events per tenant.
 *
 * <p>This buffer accumulates event counts in memory and provides drain operations for periodic
 * flushing to the database. Uses {@link LongAdder} for high-throughput concurrent increments.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * StatisticsBuffer buffer = new StatisticsBuffer(tenantId);
 * buffer.increment(LocalDate.now(), "login_success");
 * buffer.increment(LocalDate.now(), "login_success");
 *
 * // Later, flush to database
 * List<StatisticsEventRecord> records = buffer.drainAndGetRecords();
 * repository.batchUpsert(records);
 * }</pre>
 */
public class StatisticsBuffer {

  private final TenantIdentifier tenantId;

  /** Event counts: key = "date:eventType", value = count */
  private final ConcurrentHashMap<String, LongAdder> eventCounts = new ConcurrentHashMap<>();

  public StatisticsBuffer(TenantIdentifier tenantId) {
    this.tenantId = tenantId;
  }

  public TenantIdentifier tenantId() {
    return tenantId;
  }

  /**
   * Increment the count for an event type on a specific date.
   *
   * @param date the date of the event
   * @param eventType the type of event
   */
  public void increment(LocalDate date, String eventType) {
    String key = buildKey(date, eventType);
    eventCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
  }

  /**
   * Increment the count for an event type by a specific amount.
   *
   * @param date the date of the event
   * @param eventType the type of event
   * @param count the amount to increment
   */
  public void add(LocalDate date, String eventType, long count) {
    String key = buildKey(date, eventType);
    eventCounts.computeIfAbsent(key, k -> new LongAdder()).add(count);
  }

  /**
   * Get the current size of the event buffer (number of unique event type + date combinations).
   *
   * @return the buffer size
   */
  public int size() {
    return eventCounts.size();
  }

  /**
   * Get the total count of all events in the buffer.
   *
   * @return the total event count
   */
  public long totalEventCount() {
    return eventCounts.values().stream().mapToLong(LongAdder::sum).sum();
  }

  /**
   * Drain the buffer and return all accumulated records.
   *
   * <p>This method atomically resets all counters and returns the accumulated values. Thread-safe
   * for concurrent access.
   *
   * @return list of statistics event records
   */
  public List<StatisticsEventRecord> drainAndGetRecords() {
    List<StatisticsEventRecord> records = new ArrayList<>();

    eventCounts.forEach(
        (key, counter) -> {
          long count = counter.sumThenReset();
          if (count > 0) {
            StatisticsEventRecord record = parseKeyToRecord(key, count);
            if (record != null) {
              records.add(record);
            }
          }
        });

    // Clean up empty entries
    eventCounts.entrySet().removeIf(entry -> entry.getValue().sum() == 0);

    return records;
  }

  private String buildKey(LocalDate date, String eventType) {
    return date.toString() + ":" + eventType;
  }

  private StatisticsEventRecord parseKeyToRecord(String key, long count) {
    int separatorIndex = key.indexOf(':');
    if (separatorIndex < 0) {
      return null;
    }
    String dateStr = key.substring(0, separatorIndex);
    String eventType = key.substring(separatorIndex + 1);
    LocalDate date = LocalDate.parse(dateStr);
    return new StatisticsEventRecord(tenantId, date, eventType, count);
  }
}
