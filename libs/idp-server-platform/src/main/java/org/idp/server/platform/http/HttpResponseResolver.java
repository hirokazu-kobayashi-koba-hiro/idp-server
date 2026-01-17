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

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.condition.ConditionEvaluator;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class HttpResponseResolver {

  /**
   * Resolves HTTP response using the provided configurations.
   *
   * @param httpResponse HTTP response from external service
   * @param configs Resolution configurations
   * @return Resolved HttpRequestResult
   */
  public static HttpRequestResult resolve(
      HttpResponse<String> httpResponse, HttpResponseResolveConfigs configs) {

    int statusCode = httpResponse.statusCode();
    Map<String, String> headers =
        httpResponse.headers().map().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getFirst()));
    JsonNodeWrapper body = resolveResponseBody(httpResponse);

    if (configs == null || configs.isEmpty()) {
      return new HttpRequestResult(httpResponse.statusCode(), httpResponse.headers().map(), body);
    }

    JsonPathWrapper resultContext = createResultContext(statusCode, headers, body);
    HttpResponseResolveConfig matchedConfig = findMatchingConfig(resultContext, configs);

    if (matchedConfig == null) {
      return new HttpRequestResult(httpResponse.statusCode(), httpResponse.headers().map(), body);
    }

    return new HttpRequestResult(
        matchedConfig.mappedStatusCode(), httpResponse.headers().map(), body);
  }

  private static HttpResponseResolveConfig findMatchingConfig(
      JsonPathWrapper resultContext, HttpResponseResolveConfigs configs) {

    for (HttpResponseResolveConfig config : configs.configs()) {
      boolean matches =
          ConditionEvaluator.evaluate(config.conditions(), config.matchMode(), resultContext);
      if (matches) {
        return config;
      }
    }

    return null;
  }

  private static JsonPathWrapper createResultContext(
      int statusCode, Map<String, String> headers, JsonNodeWrapper body) {
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    Map<String, Object> mergedData = new HashMap<>();
    mergedData.put("status_code", statusCode);
    mergedData.put("response_headers", headers);
    if (body.isArray()) {
      mergedData.put("response_body", body.toListAsMap());
    } else {
      mergedData.put("response_body", body.toMap());
    }

    String mergedJson = jsonConverter.write(mergedData);
    return new JsonPathWrapper(mergedJson);
  }

  /**
   * Converts response body string to JsonNodeWrapper.
   *
   * @param httpResponse HTTP response
   * @return JsonNodeWrapper representation of body
   */
  public static JsonNodeWrapper resolveResponseBody(HttpResponse<String> httpResponse) {
    if (httpResponse.body() == null || httpResponse.body().isEmpty()) {
      return JsonNodeWrapper.empty();
    }

    return JsonNodeWrapper.fromString(httpResponse.body());
  }

  /**
   * Creates an HttpRequestResult from a network exception with appropriate status code mapping.
   *
   * <p>This method converts network-level exceptions into HTTP response objects with semantically
   * appropriate status codes for consistent error handling and retry logic.
   *
   * @param e the exception that occurred during HTTP request execution
   * @return HttpRequestResult with mapped status code and error details
   */
  public static HttpRequestResult resolveException(Exception e) {
    int statusCode = mapExceptionToStatusCode(e);

    Map<String, Object> message = new HashMap<>();
    message.put("error", "network_error");
    message.put("error_description", e.getMessage());
    message.put("exception_type", e.getClass().getSimpleName());

    // Add machine-readable retry information
    Map<String, Object> retryInfo = new HashMap<>();
    retryInfo.put("retryable", isRetryableExceptionType(e));
    retryInfo.put("reason", getRetryableReason(e));
    retryInfo.put("category", getExceptionCategory(e));
    message.put("retry_info", retryInfo);

    return new HttpRequestResult(statusCode, Map.of(), JsonNodeWrapper.fromObject(message));
  }

  public static HttpRequestResult resolveException(Exception e, String errorType) {
    int statusCode = mapExceptionToStatusCode(e);

    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", errorType);
    errorBody.put("error_description", e.getMessage());
    errorBody.put("exception_type", e.getClass().getSimpleName());
    errorBody.put("retryable", false);

    // Add machine-readable retry information
    Map<String, Object> retryInfo = new HashMap<>();
    retryInfo.put("retryable", false);
    retryInfo.put("reason", "max_retries_exceeded");
    retryInfo.put("category", getExceptionCategory(e));
    retryInfo.put("original_reason", getRetryableReason(e));
    errorBody.put("retry_info", retryInfo);

    return new HttpRequestResult(statusCode, Map.of(), JsonNodeWrapper.fromObject(errorBody));
  }

  /**
   * Determines if an exception type is generally considered retryable.
   *
   * @param e the exception to evaluate
   * @return true if the exception type is typically retryable
   */
  private static boolean isRetryableExceptionType(Exception e) {
    return e instanceof java.net.ConnectException
        || e instanceof java.net.SocketTimeoutException
        || e instanceof java.net.http.HttpTimeoutException
        || e instanceof InterruptedException
        || e instanceof IOException;
  }

  /**
   * Gets a machine-readable reason for why an exception is or isn't retryable.
   *
   * @param e the exception to categorize
   * @return a string describing the retry reason
   */
  private static String getRetryableReason(Exception e) {
    if (e instanceof java.net.ConnectException) {
      return "connection_failed";
    } else if (e instanceof java.net.SocketTimeoutException) {
      return "socket_timeout";
    } else if (e instanceof java.net.http.HttpTimeoutException) {
      return "http_timeout";
    } else if (e instanceof InterruptedException) {
      return "thread_interrupted";
    } else if (e instanceof IOException) {
      return "io_error";
    } else {
      return "unknown_error";
    }
  }

  /**
   * Categorizes an exception into a broad category for retry decision making.
   *
   * @param e the exception to categorize
   * @return a string representing the exception category
   */
  private static String getExceptionCategory(Exception e) {
    if (e instanceof java.net.ConnectException) {
      return "network_connectivity";
    } else if (e instanceof java.net.SocketTimeoutException
        || e instanceof java.net.http.HttpTimeoutException) {
      return "timeout";
    } else if (e instanceof InterruptedException) {
      return "interruption";
    } else if (e instanceof IOException) {
      return "io_failure";
    } else {
      return "unexpected";
    }
  }

  /**
   * Maps network exceptions to appropriate HTTP status codes for consistent error classification.
   *
   * <p>This mapping enables proper retry behavior by converting network-level failures into HTTP
   * status codes that can be evaluated against retry configuration.
   *
   * <h3>Exception Mapping</h3>
   *
   * <ul>
   *   <li>{@link java.net.ConnectException} → 503 (Service Unavailable) - Cannot establish
   *       connection
   *   <li>{@link java.net.SocketTimeoutException} → 504 (Gateway Timeout) - Network timeout
   *   <li>{@link java.net.http.HttpTimeoutException} → 504 (Gateway Timeout) - HTTP client timeout
   *   <li>{@link InterruptedException} → 503 (Service Unavailable) - Thread interruption
   *   <li>{@link IOException} → 502 (Bad Gateway) - General I/O failure
   *   <li>Other exceptions → 500 (Internal Server Error) - Fallback for unexpected errors
   * </ul>
   *
   * @param e the exception to map
   * @return HTTP status code representing the exception type
   */
  private static int mapExceptionToStatusCode(Exception e) {
    // For HttpNetworkErrorException, check the cause for proper status code mapping
    Throwable exceptionToCheck = e;
    if (e instanceof HttpNetworkErrorException && e.getCause() != null) {
      exceptionToCheck = e.getCause();
    }

    if (exceptionToCheck instanceof java.net.ConnectException) {
      return 503; // Service Unavailable
    } else if (exceptionToCheck instanceof java.net.SocketTimeoutException) {
      return 504; // Gateway Timeout
    } else if (exceptionToCheck instanceof java.net.http.HttpTimeoutException) {
      return 504; // Gateway Timeout
    } else if (exceptionToCheck instanceof InterruptedException) {
      return 503; // Service Unavailable
    } else if (exceptionToCheck instanceof IOException) {
      return 502; // Bad Gateway
    } else {
      return 500; // Internal Server Error
    }
  }
}
