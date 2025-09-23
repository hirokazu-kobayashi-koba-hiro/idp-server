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

import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public interface HttpRequestExecutionConfigInterface {

  HttpRequestUrl httpRequestUrl();

  HttpMethod httpMethod();

  HttpRequestAuthType httpRequestAuthType();

  boolean hasOAuthAuthorization();

  OAuthAuthorizationConfiguration oauthAuthorization();

  boolean hasHmacAuthentication();

  HmacAuthenticationConfig hmacAuthentication();

  HttpRequestMappingRules pathMappingRules();

  HttpRequestMappingRules headerMappingRules();

  HttpRequestMappingRules bodyMappingRules();

  HttpRequestMappingRules queryMappingRules();

  default boolean hasRetryConfiguration() {
    return false;
  }

  default HttpRetryConfiguration retryConfiguration() {
    return HttpRetryConfiguration.noRetry();
  }

  /**
   * Creates HttpRetryConfiguration from JSON configuration.
   *
   * <p>Expected JSON format:
   *
   * <pre>{@code
   * {
   *   "max_retries": 3,
   *   "backoff_delays": [1000, 5000, 30000],
   *   "retryable_status_codes": [500, 502, 503, 504, 408, 429],
   *   "idempotency_required": false,
   *   "strategy": "EXPONENTIAL_BACKOFF"
   * }
   * }</pre>
   *
   * @param retryConfigJson JSON object containing retry configuration
   * @return configured HttpRetryConfiguration instance
   */
  static HttpRetryConfiguration createRetryConfigurationFromJson(
      org.idp.server.platform.json.JsonNodeWrapper retryConfigJson) {

    if (retryConfigJson == null || !retryConfigJson.exists()) {
      return HttpRetryConfiguration.noRetry();
    }

    HttpRetryConfiguration.Builder builder = HttpRetryConfiguration.builder();

    // Max retries
    if (retryConfigJson.contains("max_retries")) {
      builder.maxRetries(retryConfigJson.getValueAsInt("max_retries"));
    }

    // Backoff delays
    if (retryConfigJson.contains("backoff_delays")) {
      java.util.List<org.idp.server.platform.json.JsonNodeWrapper> delayNodes =
          retryConfigJson.getValueAsJsonNodeList("backoff_delays");

      java.time.Duration[] delays =
          delayNodes.stream()
              .mapToInt(node -> node.asInt())
              .mapToObj(millis -> java.time.Duration.ofMillis(millis))
              .toArray(java.time.Duration[]::new);

      if (delays.length > 0) {
        builder.backoffDelays(delays);
      }
    }

    // Retryable status codes
    if (retryConfigJson.contains("retryable_status_codes")) {
      java.util.List<org.idp.server.platform.json.JsonNodeWrapper> statusNodes =
          retryConfigJson.getValueAsJsonNodeList("retryable_status_codes");

      java.util.Set<Integer> statusCodes =
          statusNodes.stream()
              .mapToInt(node -> node.asInt())
              .boxed()
              .collect(java.util.stream.Collectors.toSet());

      builder.retryableStatusCodes(statusCodes);
    }

    // Idempotency required
    if (retryConfigJson.contains("idempotency_required")) {
      builder.idempotencyRequired(retryConfigJson.getValueAsBoolean("idempotency_required"));
    }

    // Strategy
    if (retryConfigJson.contains("strategy")) {
      builder.strategy(retryConfigJson.getValueOrEmptyAsString("strategy"));
    }

    return builder.build();
  }
}
