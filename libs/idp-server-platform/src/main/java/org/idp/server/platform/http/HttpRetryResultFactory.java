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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;

/**
 * Factory for creating HTTP request results for retry-related error scenarios.
 *
 * <p>This class provides standardized result objects for various retry failure conditions, ensuring
 * consistent error reporting across the retry mechanism.
 *
 * <h3>Result Types</h3>
 *
 * <ul>
 *   <li><strong>Max Retries Exceeded</strong>: Request failed after all retry attempts
 *   <li><strong>Interrupted</strong>: Retry wait was interrupted by thread interruption
 *   <li><strong>Unexpected Termination</strong>: Retry loop terminated unexpectedly
 *   <li><strong>Unexpected Exception</strong>: Caught unexpected exception during retry
 * </ul>
 */
public class HttpRetryResultFactory {

  /**
   * Creates a result for when maximum retry attempts have been exceeded.
   *
   * <p>The result preserves the final status code and headers from the last attempt, while adding
   * comprehensive retry context information for debugging and monitoring.
   *
   * @param lastResult the result from the final retry attempt
   * @param config the retry configuration that was used
   * @return HttpRequestResult with max retries exceeded error
   */
  public static HttpRequestResult maxRetriesExceeded(
      HttpRequestResult lastResult, HttpRetryConfiguration config) {

    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", "max_retries_exceeded");
    errorBody.put(
        "error_description", String.format("Request failed after %d retries", config.maxRetries()));
    errorBody.put("max_retries", config.maxRetries());
    errorBody.put("final_status_code", lastResult.statusCode());
    errorBody.put("retryable", false);

    // Add enhanced context information
    Map<String, Object> context = new HashMap<>();
    context.put("retry_strategy", config.strategy());
    context.put("total_attempts", config.maxRetries() + 1);
    context.put("backoff_delays", config.backoffDelays());
    context.put("retryable_status_codes", config.retryableStatusCodes());
    errorBody.put("retry_context", context);

    return new HttpRequestResult(
        lastResult.statusCode(), lastResult.headers(), JsonNodeWrapper.fromObject(errorBody));
  }

  /**
   * Creates a result for when retry wait was interrupted.
   *
   * <p>This typically occurs when the thread executing the retry is interrupted during the backoff
   * sleep period. Returns status code 499 (Client Closed Request).
   *
   * @return HttpRequestResult with interrupted error
   */
  public static HttpRequestResult interrupted() {
    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", "retry_interrupted");
    errorBody.put("error_description", "Retry wait was interrupted");
    errorBody.put("retryable", false);

    return new HttpRequestResult(499, Map.of(), JsonNodeWrapper.fromObject(errorBody));
  }

  /**
   * Creates a result for unexpected retry loop termination.
   *
   * <p>This is a defensive result that should never occur in normal operation. If this result is
   * returned, it indicates a bug in the retry logic. Returns status code 499.
   *
   * @return HttpRequestResult with unexpected termination error
   */
  public static HttpRequestResult unexpectedTermination() {
    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", "unexpected_retry_termination");
    errorBody.put("error_description", "Retry loop terminated unexpectedly");
    errorBody.put("retryable", false);

    return new HttpRequestResult(499, Map.of(), JsonNodeWrapper.fromObject(errorBody));
  }

  /**
   * Creates a result for unexpected exceptions caught during retry.
   *
   * <p>This handles exceptions that were not anticipated by the normal retry flow. Returns status
   * code 500 (Internal Server Error).
   *
   * @param e the unexpected exception that occurred
   * @return HttpRequestResult with exception details
   */
  public static HttpRequestResult unexpectedException(Exception e) {
    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", "unexpected_exception");
    errorBody.put("error_description", e.getMessage());
    errorBody.put("exception_type", e.getClass().getSimpleName());
    errorBody.put("retryable", false);

    return new HttpRequestResult(500, Map.of(), JsonNodeWrapper.fromObject(errorBody));
  }
}
