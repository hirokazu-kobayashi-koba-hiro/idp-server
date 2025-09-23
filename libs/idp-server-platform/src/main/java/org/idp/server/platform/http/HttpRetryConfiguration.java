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

package org.idp.server.platform.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.idp.server.platform.json.JsonReadable;

/**
 * Configuration for HTTP request retry mechanism with exponential backoff strategy.
 *
 * <p>This class provides comprehensive retry configuration for handling transient network failures
 * and server errors in HTTP requests. It supports customizable retry attempts, backoff delays,
 * retryable conditions, and idempotency management.
 *
 * <h3>Default Configuration</h3>
 *
 * <ul>
 *   <li>Maximum retries: 3
 *   <li>Backoff delays: 1s → 5s → 30s (exponential progression)
 *   <li>Retryable status codes: 408, 429, 500, 502, 503, 504
 *   <li>Retryable exceptions: IOException, ConnectException, SocketTimeoutException,
 *       HttpTimeoutException
 *   <li>Idempotency: disabled by default
 * </ul>
 *
 * <h3>Usage Examples</h3>
 *
 * <pre>{@code
 * // Default retry configuration
 * HttpRetryConfiguration defaultConfig = HttpRetryConfiguration.defaultRetry();
 *
 * // Custom configuration with idempotency
 * HttpRetryConfiguration customConfig = HttpRetryConfiguration.builder()
 *     .maxRetries(5)
 *     .backoffDelays(Duration.ofSeconds(2), Duration.ofSeconds(10))
 *     .idempotencyRequired(true)
 *     .build();
 *
 * // No retry configuration
 * HttpRetryConfiguration noRetry = HttpRetryConfiguration.noRetry();
 * }</pre>
 *
 * <h3>Retryable Conditions</h3>
 *
 * <p>Retries are triggered based on:
 *
 * <ul>
 *   <li><strong>Status Codes</strong>: Server errors (5xx) and specific client errors (408, 429)
 *   <li><strong>Network Exceptions</strong>: Connection failures, timeouts, and I/O errors
 *   <li><strong>Custom Conditions</strong>: User-defined status codes and exception types
 * </ul>
 *
 * @see HttpRequestExecutor#executeWithRetry(java.net.http.HttpRequest, HttpRetryConfiguration)
 * @see Builder
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HttpRetryConfiguration implements JsonReadable {

  private int maxRetries = 0;
  private Duration[] backoffDelays = new Duration[0];
  private Set<Integer> retryableStatusCodes = Set.of();
  private Set<Class<? extends Exception>> retryableExceptions = Set.of();
  private boolean idempotencyRequired = false;
  private String strategy = "EXPONENTIAL_BACKOFF";

  public HttpRetryConfiguration() {}

  private HttpRetryConfiguration(Builder builder) {
    this.maxRetries = builder.maxRetries;
    this.backoffDelays = builder.backoffDelays.clone();
    this.retryableStatusCodes = Set.copyOf(builder.retryableStatusCodes);
    this.retryableExceptions = Set.copyOf(builder.retryableExceptions);
    this.idempotencyRequired = builder.idempotencyRequired;
    this.strategy = builder.strategy;
  }

  /**
   * Creates a default retry configuration with standard settings.
   *
   * @return configuration with 3 max retries, exponential backoff (1s→5s→30s), and standard
   *     retryable conditions
   */
  public static HttpRetryConfiguration defaultRetry() {
    return new Builder().build();
  }

  /**
   * Creates a configuration that disables retries completely.
   *
   * @return configuration with 0 max retries, effectively disabling retry mechanism
   */
  public static HttpRetryConfiguration noRetry() {
    return new Builder().maxRetries(0).build();
  }

  /**
   * Creates a new builder for customizing retry configuration.
   *
   * @return a new {@link Builder} instance with default values
   */
  public static Builder builder() {
    return new Builder();
  }

  public int maxRetries() {
    return maxRetries;
  }

  public Duration[] backoffDelays() {
    return backoffDelays == null ? new Duration[0] : backoffDelays.clone();
  }

  public Set<Integer> retryableStatusCodes() {
    return retryableStatusCodes;
  }

  public Set<Class<? extends Exception>> retryableExceptions() {
    return retryableExceptions;
  }

  public boolean idempotencyRequired() {
    return idempotencyRequired;
  }

  public String strategy() {
    return strategy;
  }

  /**
   * Converts this retry configuration to a Map for JSON serialization.
   *
   * @return Map representation of this retry configuration
   */
  public Map<String, Object> toMap() {
    Map<String, Object> retryMap = new HashMap<>();
    retryMap.put("max_retries", maxRetries);
    retryMap.put(
        "backoff_delays",
        java.util.Arrays.stream(backoffDelays)
            .mapToLong(duration -> duration.toMillis())
            .boxed()
            .toList());
    retryMap.put("retryable_status_codes", retryableStatusCodes);
    retryMap.put("idempotency_required", idempotencyRequired);
    retryMap.put("strategy", strategy);
    return retryMap;
  }

  public static class Builder {
    private int maxRetries = 3;
    private Duration[] backoffDelays =
        new Duration[] {
          Duration.ofSeconds(1), // 1st retry
          Duration.ofSeconds(5), // 2nd retry
          Duration.ofSeconds(30) // 3rd retry
        };
    private Set<Integer> retryableStatusCodes = Set.of(500, 502, 503, 504, 408, 429);
    private Set<Class<? extends Exception>> retryableExceptions =
        Set.of(
            java.io.IOException.class,
            java.net.ConnectException.class,
            java.net.SocketTimeoutException.class,
            java.net.http.HttpTimeoutException.class);
    private boolean idempotencyRequired = false;
    private String strategy = "EXPONENTIAL_BACKOFF";

    public Builder maxRetries(int maxRetries) {
      if (maxRetries < 0) {
        throw new IllegalArgumentException("maxRetries must be non-negative");
      }
      this.maxRetries = maxRetries;
      return this;
    }

    public Builder backoffDelays(Duration... delays) {
      Objects.requireNonNull(delays, "backoffDelays cannot be null");
      if (delays.length == 0) {
        throw new IllegalArgumentException("backoffDelays cannot be empty");
      }
      for (Duration delay : delays) {
        if (delay.isNegative()) {
          throw new IllegalArgumentException("backoffDelays cannot contain negative durations");
        }
      }
      this.backoffDelays = delays.clone();
      return this;
    }

    public Builder retryableStatusCodes(Set<Integer> statusCodes) {
      Objects.requireNonNull(statusCodes, "retryableStatusCodes cannot be null");
      this.retryableStatusCodes = statusCodes;
      return this;
    }

    public Builder retryableExceptions(Set<Class<? extends Exception>> exceptions) {
      Objects.requireNonNull(exceptions, "retryableExceptions cannot be null");
      this.retryableExceptions = exceptions;
      return this;
    }

    public Builder idempotencyRequired(boolean required) {
      this.idempotencyRequired = required;
      return this;
    }

    public Builder strategy(String strategy) {
      Objects.requireNonNull(strategy, "strategy cannot be null");
      this.strategy = strategy;
      return this;
    }

    public HttpRetryConfiguration build() {
      return new HttpRetryConfiguration(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HttpRetryConfiguration that = (HttpRetryConfiguration) o;
    return maxRetries == that.maxRetries
        && idempotencyRequired == that.idempotencyRequired
        && java.util.Arrays.equals(backoffDelays, that.backoffDelays)
        && Objects.equals(retryableStatusCodes, that.retryableStatusCodes)
        && Objects.equals(retryableExceptions, that.retryableExceptions)
        && Objects.equals(strategy, that.strategy);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            maxRetries, retryableStatusCodes, retryableExceptions, idempotencyRequired, strategy);
    result = 31 * result + java.util.Arrays.hashCode(backoffDelays);
    return result;
  }

  @Override
  public String toString() {
    return "HttpRetryConfiguration{"
        + "maxRetries="
        + maxRetries
        + ", backoffDelays="
        + java.util.Arrays.toString(backoffDelays)
        + ", retryableStatusCodes="
        + retryableStatusCodes
        + ", retryableExceptions="
        + retryableExceptions
        + ", idempotencyRequired="
        + idempotencyRequired
        + ", strategy='"
        + strategy
        + '\''
        + '}';
  }
}
