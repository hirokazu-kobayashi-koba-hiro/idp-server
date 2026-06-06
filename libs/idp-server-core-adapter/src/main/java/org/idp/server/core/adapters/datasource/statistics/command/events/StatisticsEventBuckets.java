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

package org.idp.server.core.adapters.datasource.statistics.command.events;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Bucket id picker for statistics_events write distribution (Issue #1443).
 *
 * <p>Writes to (tenant_id, stat_date, event_type) historically contended on a single hot row.
 * Adding a {@code bucket_id} dimension and scattering writes across N buckets removes the
 * contention. Reads aggregate with {@code SUM(count) ... GROUP BY tenant_id, stat_date, event_type}
 * so the bucket dimension is transparent to consumers.
 *
 * <p>{@link ThreadLocalRandom} is used to pick a bucket per call. It is cheap (no synchronization)
 * and produces a uniform distribution across worker threads. Compared to {@code
 * Thread.currentThread().getId() % N}, this also distributes writes from virtual threads, which may
 * share or rotate ids more aggressively.
 */
public final class StatisticsEventBuckets {

  /**
   * Default number of buckets per (tenant_id, stat_date, event_type) tuple.
   *
   * <p>Power-of-two value keeps modulo cheap and gives sufficient parallelism for typical workload
   * (Connection Pool 30-100 writers per node). Larger values can be considered if hot tenants
   * remain contended; smaller values save row count but reduce contention relief.
   *
   * <p>Override at startup via system property {@code idp.statistics.bucket_count} or environment
   * variable {@code IDP_STATISTICS_BUCKET_COUNT} (useful for benchmarking different N).
   *
   * <p>The value 32 is grounded in measurement: see {@code
   * performance-test/db-bench/REPORT-issue-1443.md} for the N=1/32/128 benchmark results that
   * informed this default.
   */
  static final int DEFAULT_BUCKET_COUNT = 32;

  /** Resolved bucket count at JVM startup. */
  static final int BUCKET_COUNT = resolveBucketCount();

  private StatisticsEventBuckets() {}

  private static int resolveBucketCount() {
    String value = System.getProperty("idp.statistics.bucket_count");
    if (value == null || value.isBlank()) {
      value = System.getenv("IDP_STATISTICS_BUCKET_COUNT");
    }
    if (value == null || value.isBlank()) {
      return DEFAULT_BUCKET_COUNT;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed < 1) {
        return DEFAULT_BUCKET_COUNT;
      }
      return parsed;
    } catch (NumberFormatException e) {
      return DEFAULT_BUCKET_COUNT;
    }
  }

  /** Returns a bucket id in {@code [0, BUCKET_COUNT)} for the current writer. */
  public static int pickBucketId() {
    if (BUCKET_COUNT == 1) {
      return 0;
    }
    return ThreadLocalRandom.current().nextInt(BUCKET_COUNT);
  }
}
