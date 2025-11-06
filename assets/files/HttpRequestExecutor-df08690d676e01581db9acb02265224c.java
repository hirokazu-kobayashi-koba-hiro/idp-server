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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

/**
 * Executes HTTP requests with OAuth authentication and retry support.
 *
 * <p>Provides configuration-driven and direct HTTP execution with:
 *
 * <ul>
 *   <li>OAuth 2.0 Bearer token authentication
 *   <li>Automatic retry with exponential backoff (delegated to {@link HttpRetryStrategy})
 *   <li>Response resolution with configurable conditions (delegated to {@link
 *       HttpResponseResolver})
 *   <li>Request building from configuration (delegated to {@link HttpRequestBuilder})
 * </ul>
 *
 * <h3>Usage Examples</h3>
 *
 * <pre>{@code
 * // Configuration-based execution
 * HttpRequestExecutor executor = new HttpRequestExecutor(httpClient, oauthResolvers);
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
 * @see OAuthAuthorizationConfiguration
 */
public class HttpRequestExecutor {

  HttpClient httpClient;
  OAuthAuthorizationResolvers oAuthAuthorizationResolvers;
  HttpRequestBuilder requestBuilder;
  HttpRetryStrategy retryStrategy;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestExecutor.class);

  /**
   * Creates a new HttpRequestExecutor with default JSON converter.
   *
   * @param httpClient the HTTP client for executing requests
   * @param oAuthAuthorizationResolvers OAuth resolvers for token resolution
   */
  public HttpRequestExecutor(
      HttpClient httpClient, OAuthAuthorizationResolvers oAuthAuthorizationResolvers) {
    this.httpClient = httpClient;
    this.oAuthAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.requestBuilder = new HttpRequestBuilder(oAuthAuthorizationResolvers);
    this.retryStrategy = new HttpRetryStrategy();
  }

  /**
   * Creates a new HttpRequestExecutor with custom JSON converter.
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
    this.requestBuilder = new HttpRequestBuilder(oAuthAuthorizationResolvers, jsonConverter);
    this.retryStrategy = new HttpRetryStrategy();
  }

  /**
   * Executes HTTP request based on configuration with dynamic parameter mapping.
   *
   * <p>Request building is delegated to {@link HttpRequestBuilder}.
   *
   * @param configuration request configuration (URL, method, auth, mapping rules)
   * @param httpRequestBaseParams parameters to map into request
   * @return HTTP request result
   */
  public HttpRequestResult execute(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {

    HttpRequest httpRequest = requestBuilder.build(configuration, httpRequestBaseParams);
    HttpResponseResolveConfigs responseResolveConfigs = configuration.responseResolveConfigs();

    if (configuration.hasRetryConfiguration()) {
      return executeWithRetryAndCriteria(
          httpRequest, configuration.retryConfiguration(), responseResolveConfigs);
    }

    return executeWithCriteria(httpRequest, responseResolveConfigs);
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
   * <p>Network exceptions are mapped to HTTP status codes by {@link HttpResponseResolver}.
   *
   * @param httpRequest request to execute
   * @return HTTP request result
   */
  public HttpRequestResult execute(HttpRequest httpRequest) {
    try {
      log.info("Http Request: {} {}", httpRequest.uri(), httpRequest.method());

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      log.debug("Http Response status: {}", httpResponse.statusCode());
      log.debug("Http Response body: {}", httpResponse.body());

      JsonNodeWrapper jsonResponse = HttpResponseResolver.resolveResponseBody(httpResponse);

      return new HttpRequestResult(
          httpResponse.statusCode(), httpResponse.headers().map(), jsonResponse);
    } catch (IOException | InterruptedException e) {
      log.warn("Http request was error: {}", e.getMessage(), e);
      return HttpResponseResolver.resolveException(e);
    }
  }

  /**
   * Executes HTTP request and evaluates response against configured conditions.
   *
   * <p>Response is evaluated by {@link HttpResponseResolver} to map status codes based on response
   * content.
   *
   * @param httpRequest the HTTP request to execute
   * @param resolveConfigs response resolution configurations, null to skip evaluation
   * @return HTTP request result with resolved status code
   */
  private HttpRequestResult executeWithCriteria(
      HttpRequest httpRequest, HttpResponseResolveConfigs resolveConfigs) {

    try {
      log.info("Http Request: {} {}", httpRequest.uri(), httpRequest.method());

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      log.debug("Http Response status: {}", httpResponse.statusCode());
      log.debug("Http Response body: {}", httpResponse.body());

      return HttpResponseResolver.resolve(httpResponse, resolveConfigs);
    } catch (IOException | InterruptedException e) {
      log.warn("Http request was error: {}", e.getMessage(), e);
      return HttpResponseResolver.resolveException(e);
    }
  }

  /**
   * Executes HTTP request with OAuth 2.0 Bearer token authentication.
   *
   * <p>Resolves access token via {@link OAuthAuthorizationResolver} and adds Authorization header.
   *
   * @param httpRequest the HTTP request to execute
   * @param oAuthConfig OAuth configuration for token resolution, null to skip authentication
   * @return HTTP request result
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
