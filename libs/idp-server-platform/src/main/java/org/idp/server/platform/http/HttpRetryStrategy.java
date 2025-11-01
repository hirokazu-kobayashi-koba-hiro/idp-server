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

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Retry strategy for HTTP requests with exponential backoff and idempotency support.
 *
 * <p>This class encapsulates all retry logic including:
 *
 * <ul>
 *   <li>Retry attempt management with configurable maximum retries
 *   <li>Exponential backoff with configurable delays
 *   <li>Server-specified Retry-After header support (RFC 7231)
 *   <li>Idempotency key management for duplicate request prevention
 *   <li>Retry判定ロジック（retryable status codes, 499 special handling）
 *   <li>Comprehensive logging with correlation IDs
 * </ul>
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * HttpRetryStrategy retryStrategy = new HttpRetryStrategy();
 * HttpRetryConfiguration retryConfig = HttpRetryConfiguration.defaultRetry();
 *
 * HttpRequestResult result = retryStrategy.executeWithRetry(
 *     httpRequest,
 *     retryConfig,
 *     httpRequestExecutor::execute
 * );
 * }</pre>
 */
public class HttpRetryStrategy {

  private final IdempotencyKeyManager idempotencyKeyManager;
  private final LoggerWrapper log;

  public HttpRetryStrategy() {
    this.idempotencyKeyManager = new IdempotencyKeyManager();
    this.log = LoggerWrapper.getLogger(HttpRetryStrategy.class);
  }

  /**
   * Executes HTTP request with retry logic.
   *
   * <p>This method implements a retry loop with the following behavior:
   *
   * <ol>
   *   <li>Execute request (possibly with idempotency key)
   *   <li>If successful, return result immediately
   *   <li>If failure is non-retryable, return error immediately
   *   <li>If retries exhausted, return max retries exceeded error
   *   <li>Otherwise, wait according to backoff strategy and retry
   * </ol>
   *
   * @param httpRequest the HTTP request to execute
   * @param retryConfig retry configuration specifying max retries and backoff delays
   * @param executor function that executes the HTTP request
   * @return HttpRequestResult from successful execution or final error
   */
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest, HttpRetryConfiguration retryConfig, HttpRequestFunction executor) {

    String correlationId = generateCorrelationId();
    String idempotencyKey = null;

    log.info(
        "Starting retry: uri={}, maxRetries={}, idempotencyRequired={}, correlationId={}",
        httpRequest.uri(),
        retryConfig.maxRetries(),
        retryConfig.idempotencyRequired(),
        correlationId);

    for (int attempt = 0; attempt <= retryConfig.maxRetries(); attempt++) {
      try {
        // Idempotency key management
        HttpRequest enhancedRequest = httpRequest;
        if (retryConfig.idempotencyRequired()) {
          if (idempotencyKey == null) {
            idempotencyKey = idempotencyKeyManager.generateKey(httpRequest);
          }
          enhancedRequest = idempotencyKeyManager.addIdempotencyKey(httpRequest, idempotencyKey);
        }

        // Execute request
        HttpRequestResult result = executor.execute(enhancedRequest);

        // Success case
        if (result.isSuccess()) {
          logSuccess(httpRequest, attempt, retryConfig, result, correlationId);
          return result;
        }

        // Retry judgment
        if (!isRetryable(result, retryConfig)) {
          logNonRetryable(httpRequest, attempt, result, correlationId);
          return result;
        }

        // Max retries check
        if (attempt == retryConfig.maxRetries()) {
          logMaxRetriesExceeded(httpRequest, retryConfig, result, correlationId);
          return HttpRetryResultFactory.maxRetriesExceeded(result, retryConfig);
        }

        // Wait before retry
        Duration delay = calculateDelay(attempt, result, retryConfig);
        if (!waitForRetry(delay, httpRequest, attempt, result, correlationId)) {
          return HttpRetryResultFactory.interrupted();
        }

      } catch (Exception e) {
        logException(httpRequest, attempt, correlationId, e);
        return HttpRetryResultFactory.unexpectedException(e);
      }
    }

    return HttpRetryResultFactory.unexpectedTermination();
  }

  /**
   * Determines if a response result is retryable according to configuration.
   *
   * <p>A result is retryable if:
   *
   * <ul>
   *   <li>Its status code is in the configured retryable status codes list
   *   <li>For 499 responses, the response body contains "retryable": true
   * </ul>
   *
   * @param result the HTTP request result to evaluate
   * @param config retry configuration with retryable status codes
   * @return true if the result should be retried, false otherwise
   */
  private boolean isRetryable(HttpRequestResult result, HttpRetryConfiguration config) {
    if (!config.retryableStatusCodes().contains(result.statusCode())) {
      return false;
    }

    // Special handling for 499 status code
    if (result.statusCode() == 499) {
      return isRetryable499Response(result);
    }

    return true;
  }

  /**
   * Determines if a 499 response should be retried based on the response body.
   *
   * <p>For 499 (Client Closed Request) responses, the retryability is determined by checking the
   * "retryable" field in the JSON response body. If the response is not valid JSON or the field is
   * missing, the response is considered non-retryable by default.
   *
   * @param result the HTTP response result with status code 499
   * @return true if the response indicates it's retryable, false otherwise
   */
  private boolean isRetryable499Response(HttpRequestResult result) {
    try {
      JsonNodeWrapper body = result.body();
      if (body == null || !body.exists()) {
        log.debug("499 response has no body, treating as non-retryable");
        return false;
      }

      if (!body.contains("retryable")) {
        log.debug("499 response missing 'retryable' field, treating as non-retryable");
        return false;
      }

      boolean retryable = body.getValueAsBoolean("retryable");
      log.debug("499 response retryable flag: {}", retryable);
      return retryable;

    } catch (Exception e) {
      log.debug(
          "Failed to parse 499 response body as JSON, treating as non-retryable: {}",
          e.getMessage());
      return false;
    }
  }

  /**
   * Calculates the retry delay, considering server-specified Retry-After header if present.
   *
   * <p>This method implements the HTTP standard by respecting the Retry-After header when present
   * in the response. If no Retry-After header is found, it falls back to the configured exponential
   * backoff strategy.
   *
   * @param attempt the current retry attempt number
   * @param result the HTTP response result that may contain Retry-After header
   * @param config the retry configuration with default backoff delays
   * @return the calculated delay duration
   */
  private Duration calculateDelay(
      int attempt, HttpRequestResult result, HttpRetryConfiguration config) {
    // Check for Retry-After header first
    Duration serverDelay = parseRetryAfterHeader(result);
    if (serverDelay != null) {
      log.debug("Using server-specified Retry-After delay: {}", serverDelay);
      return serverDelay;
    }

    // Fall back to configured backoff strategy
    return calculateBackoffDelay(attempt, config);
  }

  /**
   * Parses the Retry-After header from HTTP response.
   *
   * <p>Supports both delay-seconds format (integer) and HTTP-date format as per RFC 7231. Currently
   * only delay-seconds format is implemented.
   *
   * @param result the HTTP response result
   * @return the parsed delay duration, or null if no valid Retry-After header found
   */
  private Duration parseRetryAfterHeader(HttpRequestResult result) {
    Map<String, List<String>> headers = result.headers();
    if (headers == null) {
      return null;
    }

    // Look for Retry-After header (case-insensitive)
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if ("retry-after".equalsIgnoreCase(entry.getKey())) {
        List<String> values = entry.getValue();
        if (values != null && !values.isEmpty()) {
          String retryAfterValue = values.get(0).trim();
          try {
            // Try parsing as delay-seconds (integer)
            int delaySeconds = Integer.parseInt(retryAfterValue);
            if (delaySeconds >= 0) {
              return Duration.ofSeconds(delaySeconds);
            }
          } catch (NumberFormatException e) {
            // If not a number, could be HTTP-date format
            log.debug(
                "Retry-After header contains HTTP-date format, not supported: {}", retryAfterValue);
          }
        }
      }
    }

    return null;
  }

  /**
   * Calculates backoff delay based on retry attempt number and configuration.
   *
   * @param attempt the current retry attempt number (0-indexed)
   * @param config retry configuration with backoff delays
   * @return the delay duration for this attempt
   */
  private Duration calculateBackoffDelay(int attempt, HttpRetryConfiguration config) {
    Duration[] delays = config.backoffDelays();
    if (attempt < delays.length) {
      return delays[attempt];
    }

    // If attempt exceeds configured delays, use the last delay
    return delays[delays.length - 1];
  }

  /**
   * Waits for the specified duration before retry.
   *
   * @param delay the duration to wait
   * @param request the HTTP request being retried (for logging)
   * @param attempt the current attempt number (for logging)
   * @param result the previous result (for logging)
   * @param correlationId correlation ID for logging
   * @return true if wait completed successfully, false if interrupted
   */
  private boolean waitForRetry(
      Duration delay,
      HttpRequest request,
      int attempt,
      HttpRequestResult result,
      String correlationId) {
    try {
      log.warn(
          "HTTP request failed, retrying: uri={}, attempt={}, statusCode={}, nextDelay={}, correlationId={}",
          request.uri(),
          attempt + 1,
          result.statusCode(),
          delay,
          correlationId);

      Thread.sleep(delay.toMillis());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error(
          "HTTP request retry interrupted: uri={}, attempt={}, correlationId={}",
          request.uri(),
          attempt + 1,
          correlationId);
      return false;
    }
  }

  // ===== Logging Methods =====

  private void logSuccess(
      HttpRequest request,
      int attempt,
      HttpRetryConfiguration config,
      HttpRequestResult result,
      String correlationId) {
    if (attempt > 0) {
      log.info(
          "HTTP request succeeded after retry: method={}, uri={}, attempt={}/{}, statusCode={}, correlationId={}",
          request.method(),
          request.uri(),
          attempt + 1,
          config.maxRetries() + 1,
          result.statusCode(),
          correlationId);
    } else {
      log.info(
          "HTTP request succeeded: method={}, uri={}, statusCode={}, correlationId={}",
          request.method(),
          request.uri(),
          result.statusCode(),
          correlationId);
    }
  }

  private void logNonRetryable(
      HttpRequest request, int attempt, HttpRequestResult result, String correlationId) {
    log.warn(
        "HTTP request failed with non-retryable error: uri={}, statusCode={}, attempt={}, correlationId={}",
        request.uri(),
        result.statusCode(),
        attempt + 1,
        correlationId);
  }

  private void logMaxRetriesExceeded(
      HttpRequest request,
      HttpRetryConfiguration config,
      HttpRequestResult result,
      String correlationId) {
    log.error(
        "HTTP request failed after max retries: uri={}, maxRetries={}, finalStatusCode={}, correlationId={}",
        request.uri(),
        config.maxRetries(),
        result.statusCode(),
        correlationId);
  }

  private void logException(HttpRequest request, int attempt, String correlationId, Exception e) {
    log.error(
        "Unexpected exception in retry loop: uri={}, attempt={}, correlationId={}",
        request.uri(),
        attempt + 1,
        correlationId,
        e);
  }

  private String generateCorrelationId() {
    return "req_" + UUID.randomUUID().toString().substring(0, 8);
  }
}
