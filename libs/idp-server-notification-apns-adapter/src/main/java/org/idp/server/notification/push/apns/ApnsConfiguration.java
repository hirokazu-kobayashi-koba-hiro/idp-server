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

package org.idp.server.notification.push.apns;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.http.HttpRetryConfiguration;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.notification.NotificationTemplate;

public class ApnsConfiguration implements JsonReadable {

  String keyId;
  String teamId;
  String bundleId;
  String keyContent;
  boolean production;
  Map<String, NotificationTemplate> templates;
  // Optional send-retry tuning (#1539). When absent, defaults to a single 100ms retry.
  Integer retryMaxRetries;
  Long retryBackoffMillis;

  private static final int DEFAULT_RETRY_MAX_RETRIES = 1;
  private static final long DEFAULT_RETRY_BACKOFF_MILLIS = 100;
  // 502 covers the IOException ("too many concurrent streams") mapped in ApnsNotifier#send.
  // 4xx (BadDeviceToken / ExpiredProviderToken / Unregistered) are intentionally excluded.
  private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 500, 502, 503, 504);

  public ApnsConfiguration() {}

  public ApnsConfiguration(
      String keyId,
      String teamId,
      String bundleId,
      String keyContent,
      boolean production,
      Map<String, NotificationTemplate> templates) {
    this.keyId = keyId;
    this.teamId = teamId;
    this.bundleId = bundleId;
    this.keyContent = keyContent;
    this.production = production;
    this.templates = templates;
  }

  public String keyId() {
    return keyId;
  }

  public String teamId() {
    return teamId;
  }

  public String keyContent() {
    return keyContent;
  }

  public String bundleId() {
    return bundleId;
  }

  public boolean isProduction() {
    return production;
  }

  public NotificationTemplate findTemplate(String key) {
    return templates.getOrDefault(key, new NotificationTemplate());
  }

  /**
   * Send-retry policy for APNs, reusing the platform {@code HttpRetryStrategy}.
   *
   * <p>With no configuration present, this defaults to a <b>single retry with a 100ms backoff</b> —
   * enough to absorb the transient HTTP/2 "too many concurrent streams" race that occurs when a
   * burst oversubscribes the shared connection, while keeping the added latency bounded (CIBA
   * backchannel requests wait on the send synchronously). Retryable responses are {@code
   * 429/500/502/503/504}; the {@code IOException} raised by the stream race is mapped to 502 in
   * {@code ApnsNotifier#send} so it is covered too. Permanent 4xx (BadDeviceToken /
   * ExpiredProviderToken / Unregistered) are never retried.
   *
   * <p>Overridable per tenant via the APNs config JSON:
   *
   * <ul>
   *   <li>{@code retry_max_retries} — number of retries (default 1; {@code <= 0} disables retry)
   *   <li>{@code retry_backoff_millis} — fixed backoff in ms before each retry (default 100)
   * </ul>
   *
   * @return retry configuration applied to every APNs send (#1539)
   */
  public HttpRetryConfiguration retryConfiguration() {
    int maxRetries = retryMaxRetries != null ? retryMaxRetries : DEFAULT_RETRY_MAX_RETRIES;
    if (maxRetries <= 0) {
      return HttpRetryConfiguration.noRetry();
    }
    long backoffMillis =
        retryBackoffMillis != null ? retryBackoffMillis : DEFAULT_RETRY_BACKOFF_MILLIS;
    return HttpRetryConfiguration.builder()
        .maxRetries(maxRetries)
        .backoffDelays(Duration.ofMillis(backoffMillis))
        .retryableStatusCodes(RETRYABLE_STATUS_CODES)
        .build();
  }
}
