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
import java.net.http.HttpResponse;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

/**
 * Executes HTTP requests with OAuth authentication, retry support, and SSRF protection.
 *
 * <p>Provides configuration-driven and direct HTTP execution with:
 *
 * <ul>
 *   <li>OAuth 2.0 Bearer token authentication
 *   <li>Automatic retry with exponential backoff (delegated to {@link HttpRetryStrategy})
 *   <li>Response resolution with configurable conditions (delegated to {@link
 *       HttpResponseResolver})
 *   <li>Request building from configuration (delegated to {@link HttpRequestBuilder})
 *   <li>SSRF protection via {@link SsrfProtectedHttpClient}
 * </ul>
 *
 * <h3>Layer Architecture</h3>
 *
 * <pre>{@code
 * SsrfProtectedHttpClient (low-level: SSRF protection only, no dependencies)
 *     ↑
 * OAuthAuthorizationResolvers (mid-level: token acquisition)
 *     ↑
 * HttpRequestExecutor (high-level: OAuth attachment, retry, config-based execution)
 *     ↑
 * Application
 * }</pre>
 *
 * <h3>Usage Examples</h3>
 *
 * <pre>{@code
 * // Create with SSRF protected client and OAuth resolvers
 * HttpRequestExecutor executor = new HttpRequestExecutor(
 *     ssrfProtectedHttpClient, oauthResolvers);
 * HttpRequestResult result = executor.execute(config, params);
 *
 * // Direct execution with retry
 * HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
 * HttpRequestResult result = executor.executeWithRetry(request, retryConfig);
 *
 * // OAuth + Retry
 * HttpRequestResult result = executor.executeWithRetry(request, oauthConfig, retryConfig);
 * }</pre>
 *
 * <p><strong>Thread-safe</strong>: Can be shared across multiple threads.
 *
 * @see HttpRetryStrategy
 * @see HttpResponseResolver
 * @see HttpRequestBuilder
 * @see SsrfProtectedHttpClient
 * @see OAuthAuthorizationConfiguration
 */
public class HttpRequestExecutor {

  private static final int HTTP_UNAUTHORIZED = 401;
  private static final int HTTP_FORBIDDEN = 403;

  SsrfProtectedHttpClient ssrfProtectedHttpClient;
  OAuthAuthorizationResolvers oAuthAuthorizationResolvers;
  HttpRequestBuilder requestBuilder;
  HttpRetryStrategy retryStrategy;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestExecutor.class);

  /**
   * Creates a new HttpRequestExecutor with SSRF protection.
   *
   * @param ssrfProtectedHttpClient the SSRF-protected HTTP client
   * @param oAuthAuthorizationResolvers OAuth resolvers for token resolution
   */
  public HttpRequestExecutor(
      SsrfProtectedHttpClient ssrfProtectedHttpClient,
      OAuthAuthorizationResolvers oAuthAuthorizationResolvers) {
    this.ssrfProtectedHttpClient = ssrfProtectedHttpClient;
    this.oAuthAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.requestBuilder = new HttpRequestBuilder(oAuthAuthorizationResolvers);
    this.retryStrategy = new HttpRetryStrategy();
  }

  /**
   * Creates a new HttpRequestExecutor with custom JSON converter.
   *
   * @param ssrfProtectedHttpClient the SSRF-protected HTTP client
   * @param oAuthAuthorizationResolvers OAuth resolvers for token resolution
   * @param jsonConverter custom JSON converter for request/response handling
   */
  public HttpRequestExecutor(
      SsrfProtectedHttpClient ssrfProtectedHttpClient,
      OAuthAuthorizationResolvers oAuthAuthorizationResolvers,
      JsonConverter jsonConverter) {
    this.ssrfProtectedHttpClient = ssrfProtectedHttpClient;
    this.oAuthAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.requestBuilder = new HttpRequestBuilder(oAuthAuthorizationResolvers, jsonConverter);
    this.retryStrategy = new HttpRetryStrategy();
  }

  /**
   * Executes HTTP request based on configuration with dynamic parameter mapping.
   *
   * <p>Request building is delegated to {@link HttpRequestBuilder}. If OAuth authentication is
   * configured and the response is 401 Unauthorized or 403 Forbidden, the cached token is
   * invalidated and the request is retried once with a new token.
   *
   * @param configuration request configuration (URL, method, auth, mapping rules)
   * @param httpRequestBaseParams parameters to map into request
   * @return HTTP request result
   */
  public HttpRequestResult execute(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {

    return executeWithOAuthRetry(configuration, httpRequestBaseParams, false);
  }

  private HttpRequestResult executeWithOAuthRetry(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams,
      boolean isRetry) {

    HttpRequest httpRequest = requestBuilder.build(configuration, httpRequestBaseParams);
    HttpResponseResolveConfigs responseResolveConfigs = configuration.responseResolveConfigs();

    HttpRequestResult result;
    if (configuration.hasRetryConfiguration()) {
      result =
          executeWithRetryAndCriteria(
              httpRequest, configuration.retryConfiguration(), responseResolveConfigs);
    } else {
      result = executeWithCriteria(httpRequest, responseResolveConfigs);
    }

    // If OAuth is configured and response is 401/403, invalidate cache and retry once
    if (shouldRetryForOAuthError(result.statusCode(), isRetry)
        && configuration.httpRequestAuthType().isOauth2()
        && configuration.hasOAuthAuthorization()) {

      OAuthAuthorizationConfiguration oAuthConfig = configuration.oauthAuthorization();
      OAuthAuthorizationResolver resolver = oAuthAuthorizationResolvers.get(oAuthConfig.type());

      logOAuthRetry(result.statusCode(), httpRequest.uri());
      resolver.invalidateCache(oAuthConfig);
      return executeWithOAuthRetry(configuration, httpRequestBaseParams, true);
    }

    return result;
  }

  public HttpRequestResult get(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {
    HttpRequest httpRequest = requestBuilder.build(configuration, httpRequestBaseParams);
    return execute(httpRequest);
  }

  /**
   * Executes HTTP request with automatic exception mapping.
   *
   * <p>Network exceptions are mapped to HTTP status codes by {@link HttpResponseResolver}. SSRF
   * protection is handled by the underlying {@link SsrfProtectedHttpClient}.
   *
   * @param httpRequest request to execute
   * @return HTTP request result
   */
  public HttpRequestResult execute(HttpRequest httpRequest) {
    try {
      log.info("Http Request: {} {}", httpRequest.uri(), httpRequest.method());

      HttpResponse<String> httpResponse = ssrfProtectedHttpClient.send(httpRequest);

      logResponse(httpResponse);

      JsonNodeWrapper jsonResponse = HttpResponseResolver.resolveResponseBody(httpResponse);

      return new HttpRequestResult(
          httpResponse.statusCode(), httpResponse.headers().map(), jsonResponse);
    } catch (HttpNetworkErrorException e) {
      log.warn("Http request was error: {}", e.getMessage(), e);
      return HttpResponseResolver.resolveException(e);
    }
  }

  private void logResponse(HttpResponse<String> httpResponse) {
    if (httpResponse.statusCode() >= 400) {
      log.warn(
          "Http Response status: {}, body: {}", httpResponse.statusCode(), httpResponse.body());
    } else {
      log.debug(
          "Http Response status: {}, body: {}", httpResponse.statusCode(), httpResponse.body());
    }
  }

  /**
   * Executes HTTP request and evaluates response against configured conditions.
   *
   * <p>Response is evaluated by {@link HttpResponseResolver} to map status codes based on response
   * content. SSRF protection is handled by the underlying {@link SsrfProtectedHttpClient}.
   *
   * @param httpRequest the HTTP request to execute
   * @param resolveConfigs response resolution configurations, null to skip evaluation
   * @return HTTP request result with resolved status code
   */
  private HttpRequestResult executeWithCriteria(
      HttpRequest httpRequest, HttpResponseResolveConfigs resolveConfigs) {

    try {
      log.info("Http Request: {} {}", httpRequest.uri(), httpRequest.method());

      HttpResponse<String> httpResponse = ssrfProtectedHttpClient.send(httpRequest);

      logResponse(httpResponse);

      return HttpResponseResolver.resolve(httpResponse, resolveConfigs);
    } catch (HttpNetworkErrorException e) {
      log.warn("Http request was error: {}", e.getMessage(), e);
      return HttpResponseResolver.resolveException(e);
    }
  }

  /**
   * Executes HTTP request with OAuth 2.0 Bearer token authentication.
   *
   * <p>Resolves access token via {@link OAuthAuthorizationResolver} and adds Authorization header.
   * If the request returns 401 Unauthorized or 403 Forbidden, the cached token is invalidated and a
   * new token is fetched for a single retry attempt.
   *
   * @param httpRequest the HTTP request to execute
   * @param oAuthConfig OAuth configuration for token resolution, null to skip authentication
   * @return HTTP request result
   */
  public HttpRequestResult executeWithOAuth(
      HttpRequest httpRequest, OAuthAuthorizationConfiguration oAuthConfig) {
    return executeWithOAuthInternal(httpRequest, oAuthConfig, false);
  }

  private HttpRequestResult executeWithOAuthInternal(
      HttpRequest httpRequest, OAuthAuthorizationConfiguration oAuthConfig, boolean isRetry) {
    if (oAuthConfig == null) {
      return execute(httpRequest);
    }

    OAuthAuthorizationResolver resolver = oAuthAuthorizationResolvers.get(oAuthConfig.type());
    String accessToken = resolver.resolve(oAuthConfig);

    HttpRequest enhancedRequest = buildRequestWithAuth(httpRequest, accessToken);
    HttpRequestResult result = execute(enhancedRequest);

    // If 401 Unauthorized or 403 Forbidden and not already a retry, invalidate cache and retry once
    if (shouldRetryForOAuthError(result.statusCode(), isRetry)) {
      logOAuthRetry(result.statusCode(), httpRequest.uri());
      resolver.invalidateCache(oAuthConfig);
      return executeWithOAuthInternal(httpRequest, oAuthConfig, true);
    }

    return result;
  }

  /**
   * Determines if a retry should be attempted for OAuth authentication errors.
   *
   * @param statusCode the HTTP status code
   * @param isRetry whether this is already a retry attempt
   * @return true if a retry should be attempted
   */
  private boolean shouldRetryForOAuthError(int statusCode, boolean isRetry) {
    return isAuthenticationError(statusCode) && !isRetry;
  }

  /**
   * Checks if the status code indicates an authentication error (401 or 403).
   *
   * @param statusCode the HTTP status code
   * @return true if the status code is 401 or 403
   */
  private boolean isAuthenticationError(int statusCode) {
    return statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN;
  }

  /**
   * Logs the OAuth retry attempt with appropriate status description.
   *
   * @param statusCode the HTTP status code
   * @param uri the request URI
   */
  private void logOAuthRetry(int statusCode, java.net.URI uri) {
    String statusDescription = statusCode == HTTP_UNAUTHORIZED ? "Unauthorized" : "Forbidden";
    log.info(
        "Received {} {}, invalidating cached token and retrying: uri={}",
        statusCode,
        statusDescription,
        uri);
  }

  private HttpRequest buildRequestWithAuth(HttpRequest httpRequest, String accessToken) {
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

    return builder.build();
  }

  /**
   * Executes HTTP request with retry and response condition evaluation.
   *
   * @param httpRequest the HTTP request to execute
   * @param retryConfig retry configuration
   * @param resolveConfigs response resolution configurations, null to skip evaluation
   * @return HTTP request result with retry applied
   */
  private HttpRequestResult executeWithRetryAndCriteria(
      HttpRequest httpRequest,
      HttpRetryConfiguration retryConfig,
      HttpResponseResolveConfigs resolveConfigs) {

    return retryStrategy.executeWithRetry(
        httpRequest, retryConfig, req -> executeWithCriteria(req, resolveConfigs));
  }

  /**
   * Executes HTTP request with retry mechanism (delegated to {@link HttpRetryStrategy}).
   *
   * <p>Supports exponential backoff, idempotency keys, and Retry-After header parsing.
   *
   * @param httpRequest the HTTP request to execute
   * @param retryConfig retry configuration
   * @return HTTP request result with retry applied
   * @see HttpRetryStrategy
   */
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest, HttpRetryConfiguration retryConfig) {
    return retryStrategy.executeWithRetry(httpRequest, retryConfig, this::execute);
  }

  /**
   * Executes HTTP request with OAuth authentication and retry mechanism.
   *
   * <p>Combines OAuth Bearer token authentication with retry logic (delegated to {@link
   * HttpRetryStrategy}).
   *
   * @param httpRequest the HTTP request to execute
   * @param oAuthConfig OAuth configuration for token resolution, null to skip authentication
   * @param retryConfig retry configuration
   * @return HTTP request result with OAuth and retry applied
   */
  public HttpRequestResult executeWithRetry(
      HttpRequest httpRequest,
      OAuthAuthorizationConfiguration oAuthConfig,
      HttpRetryConfiguration retryConfig) {

    if (oAuthConfig == null) {
      return retryStrategy.executeWithRetry(httpRequest, retryConfig, this::execute);
    }

    return retryStrategy.executeWithRetry(
        httpRequest, retryConfig, req -> executeWithOAuth(req, oAuthConfig));
  }
}
