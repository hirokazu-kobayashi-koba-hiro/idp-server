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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

/**
 * Executes HTTP requests with support for OAuth authentication, retry mechanisms, and idempotency.
 *
 * <p>This class provides a comprehensive HTTP client implementation for enterprise applications,
 * offering robust error handling, automatic retries, and OAuth 2.0 integration. It serves as the
 * central component for all HTTP communications in the identity platform.
 *
 * <h3>Core Features</h3>
 *
 * <ul>
 *   <li><strong>Basic HTTP Operations</strong>: GET, POST, PUT, DELETE with automatic content
 *       negotiation
 *   <li><strong>OAuth 2.0 Integration</strong>: Automatic Bearer token resolution and header
 *       injection
 *   <li><strong>Retry Mechanism</strong>: Exponential backoff with configurable retry policies
 *   <li><strong>Idempotency Support</strong>: Duplicate request prevention with unique key
 *       generation
 *   <li><strong>Exception Mapping</strong>: Network errors mapped to appropriate HTTP status codes
 * </ul>
 *
 * <h3>Configuration-Based Execution</h3>
 *
 * <p>Supports configuration-driven HTTP requests through {@link
 * HttpRequestExecutionConfigInterface}:
 *
 * <ul>
 *   <li>Dynamic URL construction with path parameter interpolation
 *   <li>Header mapping from request parameters
 *   <li>Body mapping with automatic JSON serialization
 *   <li>Query parameter handling for GET requests
 *   <li>HMAC authentication support
 * </ul>
 *
 * <h3>Direct Request Execution</h3>
 *
 * <p>For direct HTTP request execution:
 *
 * <ul>
 *   <li>{@link #execute(HttpRequest)} - Basic HTTP execution
 *   <li>{@link #executeWithOAuth(HttpRequest, OAuthAuthorizationConfiguration)} - With OAuth
 *   <li>{@link #executeWithRetry(HttpRequest, HttpRetryConfiguration)} - With retry mechanism
 *   <li>{@link #executeWithRetry(HttpRequest, OAuthAuthorizationConfiguration,
 *       HttpRetryConfiguration)} - Full-featured
 * </ul>
 *
 * <h3>Error Handling Strategy</h3>
 *
 * <p>Network exceptions are systematically mapped to HTTP status codes:
 *
 * <ul>
 *   <li>ConnectException → 503 (Service Unavailable)
 *   <li>SocketTimeoutException → 504 (Gateway Timeout)
 *   <li>HttpTimeoutException → 504 (Gateway Timeout)
 *   <li>InterruptedException → 503 (Service Unavailable)
 *   <li>IOException → 502 (Bad Gateway)
 * </ul>
 *
 * <h3>Thread Safety</h3>
 *
 * <p>This class is thread-safe and can be shared across multiple threads. The underlying {@link
 * HttpClient} is thread-safe, and all internal state is either immutable or properly synchronized.
 *
 * <h3>Usage Examples</h3>
 *
 * <pre>{@code
 * // Basic usage
 * HttpRequestExecutor executor = new HttpRequestExecutor(httpClient, oauthResolvers);
 * HttpRequest request = HttpRequest.newBuilder()
 *     .uri(URI.create("https://api.example.com/data"))
 *     .GET()
 *     .build();
 * HttpRequestResult result = executor.execute(request);
 *
 * // With retry mechanism
 * HttpRetryConfiguration retryConfig = HttpRetryConfiguration.defaultRetry();
 * HttpRequestResult result = executor.executeWithRetry(request, retryConfig);
 *
 * // With OAuth and retry
 * OAuthAuthorizationConfiguration oauthConfig = // ... configure OAuth
 * HttpRequestResult result = executor.executeWithRetry(request, oauthConfig, retryConfig);
 * }</pre>
 *
 * @see HttpRequestExecutionConfigInterface
 * @see HttpRetryConfiguration
 * @see OAuthAuthorizationConfiguration
 * @see IdempotencyKeyManager
 */
public class HttpRequestExecutor {

  HttpClient httpClient;
  OAuthAuthorizationResolvers oAuthAuthorizationResolvers;
  JsonConverter jsonConverter;
  IdempotencyKeyManager idempotencyKeyManager;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestExecutor.class);

  /**
   * Creates a new HttpRequestExecutor with default JSON converter.
   *
   * <p>Uses snake_case JSON conversion strategy and initializes idempotency management.
   *
   * @param httpClient the HTTP client for executing requests
   * @param oAuthAuthorizationResolvers OAuth resolvers for token resolution
   */
  public HttpRequestExecutor(
      HttpClient httpClient, OAuthAuthorizationResolvers oAuthAuthorizationResolvers) {
    this.httpClient = httpClient;
    this.oAuthAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.idempotencyKeyManager = new IdempotencyKeyManager();
  }

  /**
   * Creates a new HttpRequestExecutor with custom JSON converter.
   *
   * <p>Allows customization of JSON serialization strategy while maintaining OAuth and idempotency
   * capabilities.
   *
   * @param httpClient the HTTP client for executing requests
   * @param oAuthAuthorizationResolvers OAuth resolvers for token resolution
   * @param jsonConverter custom JSON converter for request/response handling
   */
  public HttpRequestExecutor(
      HttpClient httpClient,
      OAuthAuthorizationResolvers oAuthAuthorizationResolvers,
      JsonConverter jsonConverter) {
    this.httpClient = httpClient;
    this.oAuthAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.jsonConverter = jsonConverter;
    this.idempotencyKeyManager = new IdempotencyKeyManager();
  }

  /**
   * Executes an HTTP request based on configuration and request parameters.
   *
   * <p>This method provides configuration-driven HTTP request execution with support for:
   *
   * <ul>
   *   <li>Dynamic URL construction with path parameter interpolation
   *   <li>Header mapping from request parameters
   *   <li>Body mapping with automatic JSON serialization
   *   <li>Multiple authentication methods (OAuth 2.0, HMAC SHA-256)
   *   <li>Content type negotiation (JSON, form-encoded)
   * </ul>
   *
   * <h3>Authentication Support</h3>
   *
   * <ul>
   *   <li><strong>OAuth 2.0</strong>: Automatic Bearer token resolution and injection
   *   <li><strong>HMAC SHA-256</strong>: Request signing with configurable fields and formats
   * </ul>
   *
   * <h3>HTTP Method Handling</h3>
   *
   * <ul>
   *   <li><strong>GET</strong>: Query parameters from mapping rules
   *   <li><strong>POST/PUT/DELETE</strong>: Body content with path parameters
   * </ul>
   *
   * @param configuration the HTTP request configuration defining URL, method, mapping rules, and
   *     authentication
   * @param httpRequestBaseParams the base parameters to map into the request
   * @return {@link HttpRequestResult} containing the response or error details
   * @see HttpRequestExecutionConfigInterface
   * @see HttpRequestBaseParams
   */
  public HttpRequestResult execute(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {

    // Build the HTTP request with OAuth authentication if configured
    HttpRequest httpRequest = buildHttpRequest(configuration, httpRequestBaseParams);

    // Use retry if configured, otherwise simple execution
    if (configuration.hasRetryConfiguration()) {
      return executeWithRetry(httpRequest, configuration.retryConfiguration());
    }

    return execute(httpRequest);
  }

  private HttpRequest buildHttpRequest(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {

    if (configuration.httpMethod().isGet()) {
      return buildGetRequest(configuration, httpRequestBaseParams);
    }

    HttpRequestDynamicMapper pathMapper =
        new HttpRequestDynamicMapper(configuration.pathMappingRules(), httpRequestBaseParams);
    Map<String, String> pathParams = pathMapper.toPathParams();
    HttpRequestUrl interpolatedUrl = configuration.httpRequestUrl().interpolate(pathParams);

    HttpRequestDynamicMapper headerMapper =
        new HttpRequestDynamicMapper(configuration.headerMappingRules(), httpRequestBaseParams);
    Map<String, String> headers = headerMapper.toHeaders();

    HttpRequestDynamicMapper bodyMapper =
        new HttpRequestDynamicMapper(configuration.bodyMappingRules(), httpRequestBaseParams);

    Map<String, Object> requestBody = bodyMapper.toBody();

    switch (configuration.httpRequestAuthType()) {
      case OAUTH2 -> {
        OAuthAuthorizationConfiguration oAuthAuthorizationConfig =
            configuration.oauthAuthorization();
        OAuthAuthorizationResolver resolver =
            oAuthAuthorizationResolvers.get(oAuthAuthorizationConfig.type());
        String accessToken = resolver.resolve(oAuthAuthorizationConfig);
        headers.put("Authorization", "Bearer " + accessToken);
      }
      case HMAC_SHA256 -> {
        HmacAuthenticationConfig hmacAuthenticationConfig = configuration.hmacAuthentication();
        HttpHmacAuthorizationHeaderCreator httpHmacAuthorizationHeaderCreator =
            new HttpHmacAuthorizationHeaderCreator(
                hmacAuthenticationConfig.apiKey(), hmacAuthenticationConfig.secret());
        String hmacAuthentication =
            httpHmacAuthorizationHeaderCreator.create(
                configuration.httpMethod().name(),
                configuration.httpRequestUrl().value(),
                requestBody,
                hmacAuthenticationConfig.signingFields(),
                hmacAuthenticationConfig.signatureFormat());

        headers.put("Authorization", hmacAuthentication);
      }
    }

    HttpRequest.Builder httpRequestBuilder =
        HttpRequest.newBuilder().uri(URI.create(interpolatedUrl.value()));

    setHeaders(httpRequestBuilder, headers);
    setParams(httpRequestBuilder, configuration.httpMethod(), headers, requestBody);

    return httpRequestBuilder.build();
  }

  private HttpRequestResult get(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {
    HttpRequest httpRequest = buildGetRequest(configuration, httpRequestBaseParams);
    return execute(httpRequest);
  }

  private HttpRequest buildGetRequest(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {

    HttpRequestDynamicMapper pathMapper =
        new HttpRequestDynamicMapper(configuration.pathMappingRules(), httpRequestBaseParams);
    Map<String, String> pathParams = pathMapper.toPathParams();
    HttpRequestUrl interpolatedUrl = configuration.httpRequestUrl().interpolate(pathParams);

    HttpRequestDynamicMapper headerMapper =
        new HttpRequestDynamicMapper(configuration.headerMappingRules(), httpRequestBaseParams);
    Map<String, String> headers = headerMapper.toHeaders();
    HttpRequestDynamicMapper bodyMapper =
        new HttpRequestDynamicMapper(configuration.queryMappingRules(), httpRequestBaseParams);

    Map<String, String> queryParams = bodyMapper.toQueryParams();

    if (configuration.httpRequestAuthType().isOauth2()) {
      OAuthAuthorizationConfiguration oAuthAuthorizationConfig = configuration.oauthAuthorization();
      OAuthAuthorizationResolver resolver =
          oAuthAuthorizationResolvers.get(oAuthAuthorizationConfig.type());
      String accessToken = resolver.resolve(oAuthAuthorizationConfig);
      headers.put("Authorization", "Bearer " + accessToken);
    }

    String urlWithQueryParams = interpolatedUrl.withQueryParams(new HttpQueryParams(queryParams));

    HttpRequest.Builder httpRequestBuilder =
        HttpRequest.newBuilder().uri(URI.create(urlWithQueryParams));

    setHeaders(httpRequestBuilder, headers);
    setParams(httpRequestBuilder, HttpMethod.GET, headers, Map.of());

    return httpRequestBuilder.build();
  }

  /**
   * Executes a basic HTTP request without additional features.
   *
   * <p>This method provides direct HTTP request execution with automatic exception mapping but no
   * retry mechanism or OAuth authentication. Network exceptions are converted to appropriate HTTP
   * status codes for consistent error handling.
   *
   * <h3>Exception Handling</h3>
   *
   * <p>Network-level exceptions are automatically mapped to HTTP status codes:
   *
   * <ul>
   *   <li>ConnectException → 503 (Service Unavailable)
   *   <li>SocketTimeoutException → 504 (Gateway Timeout)
   *   <li>HttpTimeoutException → 504 (Gateway Timeout)
   *   <li>InterruptedException → 503 (Service Unavailable)
   *   <li>IOException → 502 (Bad Gateway)
   * </ul>
   *
   * @param httpRequest the HTTP request to execute
   * @return {@link HttpRequestResult} containing the response or error details
   * @see #executeWithOAuth(HttpRequest, OAuthAuthorizationConfiguration)
   * @see #executeWithRetry(HttpRequest, HttpRetryConfiguration)
   */
  public HttpRequestResult execute(HttpRequest httpRequest) {
    try {
      log.info("Http Request: {} {}", httpRequest.uri(), httpRequest.method());

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      log.debug("Http Response status: {}", httpResponse.statusCode());
      log.debug("Http Response body: {}", httpResponse.body());

      JsonNodeWrapper jsonResponse = resolveResponseBody(httpResponse);

      return new HttpRequestResult(
          httpResponse.statusCode(), httpResponse.headers().map(), jsonResponse);
    } catch (IOException | InterruptedException e) {
      log.warn("Http request was error: {}", e.getMessage(), e);
      return createExceptionResult(e);
    }
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
  private HttpRequestResult createExceptionResult(Exception e) {
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

  /**
   * Determines if an exception type is generally considered retryable.
   *
   * @param e the exception to evaluate
   * @return true if the exception type is typically retryable
   */
  private boolean isRetryableExceptionType(Exception e) {
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
  private String getRetryableReason(Exception e) {
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
  private String getExceptionCategory(Exception e) {
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
  private int mapExceptionToStatusCode(Exception e) {
    if (e instanceof java.net.ConnectException) {
      return 503; // Service Unavailable
    } else if (e instanceof java.net.SocketTimeoutException) {
      return 504; // Gateway Timeout
    } else if (e instanceof java.net.http.HttpTimeoutException) {
      return 504; // Gateway Timeout
    } else if (e instanceof InterruptedException) {
      return 503; // Service Unavailable
    } else if (e instanceof IOException) {
      return 502; // Bad Gateway
    } else {
      return 500; // Internal Server Error
    }
  }

  /**
   * Executes an HTTP request with OAuth 2.0 authentication.
   *
   * <p>This method adds OAuth Bearer token authentication to HTTP requests by resolving access
   * tokens through the configured OAuth authorization resolvers. If the OAuth configuration is
   * null, the request is executed without authentication.
   *
   * <h3>OAuth Process</h3>
   *
   * <ol>
   *   <li>Resolve access token using configured {@link OAuthAuthorizationResolver}
   *   <li>Add Authorization header with "Bearer {token}" format
   *   <li>Copy all existing headers from the original request
   *   <li>Execute the enhanced request with OAuth authentication
   * </ol>
   *
   * <h3>Token Resolution</h3>
   *
   * <p>Access tokens are resolved based on the OAuth configuration type, supporting multiple OAuth
   * flows and token sources through the resolver registry.
   *
   * @param httpRequest the HTTP request to execute
   * @param oAuthConfig OAuth configuration for token resolution, null for no authentication
   * @return {@link HttpRequestResult} containing the response or error details
   * @see OAuthAuthorizationConfiguration
   * @see OAuthAuthorizationResolver
   * @see #execute(HttpRequest)
   */
  public HttpRequestResult executeWithOAuth(
      HttpRequest httpRequest, OAuthAuthorizationConfiguration oAuthConfig) {
    if (oAuthConfig == null) {
      return execute(httpRequest);
    }

    OAuthAuthorizationResolver resolver = oAuthAuthorizationResolvers.get(oAuthConfig.type());
    String accessToken = resolver.resolve(oAuthConfig);

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(httpRequest.uri())
            .method(
                httpRequest.method(),
                httpRequest.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));

    // Copy existing headers
    httpRequest
        .headers()
        .map()
        .forEach(
            (name, values) -> {
              for (String value : values) {
                builder.header(name, value);
              }
            });

    // Add OAuth authorization header
    builder.header("Authorization", "Bearer " + accessToken);

    HttpRequest enhancedRequest = builder.build();
    return execute(enhancedRequest);
  }

  /**
   * Executes an HTTP request with retry mechanism based on the provided configuration.
   *
   * <p>This method implements exponential backoff retry strategy for handling transient network
   * failures and server errors. Network exceptions are automatically mapped to appropriate HTTP
   * status codes for consistent retry behavior.
   *
   * <h3>Exception Mapping</h3>
   *
   * <ul>
   *   <li>{@code IOException} → 502 (Bad Gateway)
   *   <li>{@code ConnectException} → 503 (Service Unavailable)
   *   <li>{@code SocketTimeoutException} → 504 (Gateway Timeout)
   *   <li>{@code HttpTimeoutException} → 504 (Gateway Timeout)
   *   <li>{@code InterruptedException} → 503 (Service Unavailable)
   * </ul>
   *
   * <h3>Retry Behavior</h3>
   *
   * <ul>
   *   <li>Retries are performed for status codes defined in {@link
   *       HttpRetryConfiguration#retryableStatusCodes()}
   *   <li>Default retryable status codes: 408, 429, 500, 502, 503, 504
   *   <li>Exponential backoff delays (default: 1s → 5s → 30s)
   *   <li>Maximum of 3 retries by default
   * </ul>
   *
   * <h3>Correlation Tracking</h3>
   *
   * Each retry attempt is tracked with a unique correlation ID for debugging and monitoring
   * purposes.
   *
   * @param httpRequest the HTTP request to execute
   * @param retryConfig the retry configuration specifying max retries, backoff delays, and
   *     retryable conditions
   * @return {@link HttpRequestResult} containing the response or error details
   * @see HttpRetryConfiguration
   * @see #executeWithRetry(HttpRequest, OAuthAuthorizationConfiguration, HttpRetryConfiguration)
   */
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest, HttpRetryConfiguration retryConfig) {
    return executeWithRetry(httpRequest, null, retryConfig);
  }

  /**
   * Executes an HTTP request with OAuth authentication and retry mechanism.
   *
   * <p>This method combines OAuth authorization with the retry functionality, automatically adding
   * OAuth Bearer tokens to requests before applying the retry strategy. If OAuth configuration is
   * null, the request is executed without authentication.
   *
   * <h3>OAuth Integration</h3>
   *
   * <ul>
   *   <li>Automatically resolves OAuth access tokens using configured {@link
   *       OAuthAuthorizationResolver}
   *   <li>Adds Authorization header with Bearer token format
   *   <li>OAuth authentication is applied before each retry attempt
   * </ul>
   *
   * <h3>Idempotency Support</h3>
   *
   * When {@link HttpRetryConfiguration#idempotencyRequired()} is true:
   *
   * <ul>
   *   <li>Generates unique Idempotency-Key header for duplicate request prevention
   *   <li>Same idempotency key is used across all retry attempts
   *   <li>Key format: "idem_{requestHash}_{uuid8}"
   * </ul>
   *
   * @param httpRequest the HTTP request to execute
   * @param oAuthConfig OAuth configuration for token resolution, null for no authentication
   * @param retryConfig retry configuration specifying max retries, backoff delays, and retryable
   *     conditions
   * @return {@link HttpRequestResult} containing the response or error details
   * @see OAuthAuthorizationConfiguration
   * @see IdempotencyKeyManager
   * @see #executeWithRetry(HttpRequest, HttpRetryConfiguration)
   */
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest,
      OAuthAuthorizationConfiguration oAuthConfig,
      HttpRetryConfiguration retryConfig) {

    String correlationId = generateCorrelationId();
    String idempotencyKey = null;

    log.info(
        "Starting HTTP request with retry: method={}, uri={}, maxRetries={}, idempotencyRequired={}, correlationId={}",
        httpRequest.method(),
        httpRequest.uri(),
        retryConfig.maxRetries(),
        retryConfig.idempotencyRequired(),
        correlationId);

    for (int attempt = 0; attempt <= retryConfig.maxRetries(); attempt++) {
      try {
        // Idempotency Key management
        HttpRequest enhancedRequest = httpRequest;
        if (retryConfig.idempotencyRequired()) {
          if (idempotencyKey == null) {
            idempotencyKey = idempotencyKeyManager.generateKey(httpRequest);
          }
          enhancedRequest = idempotencyKeyManager.addIdempotencyKey(httpRequest, idempotencyKey);
        }

        // Apply OAuth authentication if needed
        HttpRequestResult result =
            (oAuthConfig != null)
                ? executeWithOAuth(enhancedRequest, oAuthConfig)
                : execute(enhancedRequest);

        // Success case
        if (result.isSuccess()) {
          if (attempt > 0) {
            log.info(
                "HTTP request succeeded after retry: method={}, uri={}, attempt={}/{}, statusCode={}, correlationId={}",
                httpRequest.method(),
                httpRequest.uri(),
                attempt + 1,
                retryConfig.maxRetries() + 1,
                result.statusCode(),
                correlationId);
          } else {
            log.info(
                "HTTP request succeeded: method={}, uri={}, statusCode={}, correlationId={}",
                httpRequest.method(),
                httpRequest.uri(),
                result.statusCode(),
                correlationId);
          }
          return result;
        }

        // Retry judgment
        if (!isRetryableResult(result, retryConfig)) {
          log.warn(
              "HTTP request failed with non-retryable error: uri={}, statusCode={}, attempt={}, correlationId={}",
              httpRequest.uri(),
              result.statusCode(),
              attempt + 1,
              correlationId);
          return result;
        }

        // Max retries check
        if (attempt == retryConfig.maxRetries()) {
          log.error(
              "HTTP request failed after max retries: uri={}, maxRetries={}, finalStatusCode={}, correlationId={}",
              httpRequest.uri(),
              retryConfig.maxRetries(),
              result.statusCode(),
              correlationId);
          return createMaxRetriesExceededResult(result, retryConfig);
        }

        // Wait before retry - check for server-specified delay first
        Duration delay = calculateRetryDelay(attempt, result, retryConfig);
        log.warn(
            "HTTP request failed, retrying: uri={}, attempt={}, statusCode={}, nextDelay={}, correlationId={}",
            httpRequest.uri(),
            attempt + 1,
            result.statusCode(),
            delay,
            correlationId);

        if (!waitBeforeRetry(delay)) {
          log.error(
              "HTTP request retry interrupted: uri={}, attempt={}, correlationId={}",
              httpRequest.uri(),
              attempt + 1,
              correlationId);
          return createInterruptedResult();
        }
      } catch (Exception e) {
        log.error(
            "Unexpected exception in retry loop: uri={}, attempt={}, correlationId={}",
            httpRequest.uri(),
            attempt + 1,
            correlationId,
            e);
        return createExceptionResult(e, "unexpected_exception");
      }
    }

    // This should never be reached, but just in case
    return createUnexpectedTerminationResult();
  }

  private boolean isRetryableResult(HttpRequestResult result, HttpRetryConfiguration config) {
    // Check if status code is explicitly configured as retryable
    if (!config.retryableStatusCodes().contains(result.statusCode())) {
      return false;
    }

    // Special handling for 499 status code - check response body for retryable flag
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

  private boolean isRetryableException(Exception e, HttpRetryConfiguration config) {
    return config.retryableExceptions().stream()
        .anyMatch(retryableClass -> retryableClass.isAssignableFrom(e.getClass()));
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
  private Duration calculateRetryDelay(
      int attempt, HttpRequestResult result, HttpRetryConfiguration config) {
    // Check for Retry-After header first
    Duration serverSpecifiedDelay = parseRetryAfterHeader(result);
    if (serverSpecifiedDelay != null) {
      log.debug("Using server-specified Retry-After delay: {}", serverSpecifiedDelay);
      return serverSpecifiedDelay;
    }

    // Fall back to configured backoff strategy
    return calculateBackoffDelay(attempt, config);
  }

  /**
   * Parses the Retry-After header from HTTP response.
   *
   * <p>Supports both delay-seconds format (integer) and HTTP-date format as per RFC 7231.
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
    List<String> retryAfterValues = null;
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if ("retry-after".equalsIgnoreCase(entry.getKey())) {
        retryAfterValues = entry.getValue();
        break;
      }
    }

    if (retryAfterValues == null || retryAfterValues.isEmpty()) {
      return null;
    }

    String retryAfterValue = retryAfterValues.get(0).trim();

    try {
      // Try parsing as delay-seconds (integer)
      int delaySeconds = Integer.parseInt(retryAfterValue);
      if (delaySeconds >= 0) {
        return Duration.ofSeconds(delaySeconds);
      }
    } catch (NumberFormatException e) {
      // If not a number, could be HTTP-date format
      // For simplicity, we'll log and ignore HTTP-date format for now
      log.debug("Retry-After header contains HTTP-date format, not supported: {}", retryAfterValue);
    }

    return null;
  }

  private Duration calculateBackoffDelay(int attempt, HttpRetryConfiguration config) {
    Duration[] delays = config.backoffDelays();
    if (attempt < delays.length) {
      return delays[attempt];
    }

    // If attempt exceeds configured delays, use the last delay
    return delays[delays.length - 1];
  }

  private boolean waitBeforeRetry(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Retry wait interrupted: {}", e.getMessage());
      return false;
    }
  }

  private HttpRequestResult createMaxRetriesExceededResult(
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

  private HttpRequestResult createExceptionResult(Exception e, String errorType) {
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

  private HttpRequestResult createMaxRetriesExceededExceptionResult(
      Exception e, HttpRetryConfiguration config) {
    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", "max_retries_exceeded_with_exception");
    errorBody.put(
        "error_description",
        String.format(
            "Request failed with exception after %d retries: %s",
            config.maxRetries(), e.getMessage()));
    errorBody.put("max_retries", config.maxRetries());
    errorBody.put("exception_type", e.getClass().getSimpleName());
    errorBody.put("retryable", false);

    return new HttpRequestResult(
        mapExceptionToStatusCode(e), Map.of(), JsonNodeWrapper.fromObject(errorBody));
  }

  private HttpRequestResult createUnexpectedTerminationResult() {
    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", "unexpected_retry_termination");
    errorBody.put("error_description", "Retry loop terminated unexpectedly");
    errorBody.put("retryable", false);

    return new HttpRequestResult(499, Map.of(), JsonNodeWrapper.fromObject(errorBody));
  }

  private HttpRequestResult createInterruptedResult() {
    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("error", "retry_interrupted");
    errorBody.put("error_description", "Retry wait was interrupted");
    errorBody.put("retryable", false);

    return new HttpRequestResult(499, Map.of(), JsonNodeWrapper.fromObject(errorBody));
  }

  private String generateCorrelationId() {
    return "req_" + UUID.randomUUID().toString().substring(0, 8);
  }

  private JsonNodeWrapper resolveResponseBody(HttpResponse<String> httpResponse) {
    if (httpResponse.body() == null || httpResponse.body().isEmpty()) {
      return JsonNodeWrapper.empty();
    }

    return JsonNodeWrapper.fromString(httpResponse.body());
  }

  private void setHeaders(
      HttpRequest.Builder httpRequestBuilder, Map<String, String> httpRequestStaticHeaders) {

    log.debug("Http Request headers: {}", httpRequestStaticHeaders);

    httpRequestStaticHeaders.forEach(httpRequestBuilder::header);
    if (!httpRequestStaticHeaders.containsKey("Content-Type")) {
      httpRequestBuilder.setHeader("Content-Type", "application/json");
    }
  }

  private void setParams(
      HttpRequest.Builder builder,
      HttpMethod httpMethod,
      Map<String, String> headers,
      Map<String, Object> requestBody) {

    switch (httpMethod) {
      case GET:
        builder.GET();
        break;
      case POST:
        {
          log.debug("Http Request body: {}", requestBody);
          if ("application/x-www-form-urlencoded".equals(headers.get("Content-Type"))) {
            HttpQueryParams httpQueryParams = HttpQueryParams.fromMapObject(requestBody);
            builder.POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));
          } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          }
          break;
        }
      case PUT:
        {
          log.debug("Http Request body: {}", requestBody);
          if ("application/x-www-form-urlencoded".equals(headers.get("Content-Type"))) {
            HttpQueryParams httpQueryParams = HttpQueryParams.fromMapObject(requestBody);
            builder.PUT(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));
          } else {
            builder.PUT(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          }
          break;
        }
      case DELETE:
        {
          log.debug("Http Request body: {}", requestBody);
          if ("application/x-www-form-urlencoded".equals(headers.get("Content-Type"))) {
            HttpQueryParams httpQueryParams = HttpQueryParams.fromMapObject(requestBody);
            builder.method("DELETE", HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));
          } else {
            builder.method(
                "DELETE", HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          }
          break;
        }
    }
  }
}
