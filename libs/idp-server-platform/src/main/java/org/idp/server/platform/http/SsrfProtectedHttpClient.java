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
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.ssrf.SsrfProtectionException;
import org.idp.server.platform.security.ssrf.SsrfProtectionValidator;
import org.idp.server.platform.system.SystemConfiguration;
import org.idp.server.platform.system.SystemConfigurationResolver;
import org.idp.server.platform.system.config.SsrfProtectionConfig;

/**
 * Low-level HTTP client wrapper with SSRF protection.
 *
 * <p>This class provides SSRF-safe HTTP request execution by validating URIs before sending
 * requests. It sits at the lowest level of the HTTP layer and has no dependencies on OAuth or other
 * high-level components.
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
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * SsrfProtectedHttpClient client = new SsrfProtectedHttpClient(
 *     HttpClient.newHttpClient(), systemConfigResolver);
 *
 * HttpRequest request = HttpRequest.newBuilder()
 *     .uri(URI.create("https://api.example.com/token"))
 *     .POST(...)
 *     .build();
 *
 * HttpResponse<String> response = client.send(request);
 * }</pre>
 *
 * @see SsrfProtectionValidator
 * @see SystemConfigurationResolver
 */
public class SsrfProtectedHttpClient {

  private final HttpClient httpClient;
  private final SystemConfigurationResolver systemConfigurationResolver;
  private final LoggerWrapper log = LoggerWrapper.getLogger(SsrfProtectedHttpClient.class);

  public SsrfProtectedHttpClient(
      HttpClient httpClient, SystemConfigurationResolver systemConfigurationResolver) {
    this.httpClient = httpClient;
    this.systemConfigurationResolver = systemConfigurationResolver;
  }

  /**
   * Sends an HTTP request with SSRF protection.
   *
   * @param request the HTTP request to send
   * @return the HTTP response
   * @throws SsrfProtectionException if the request URI is blocked by SSRF protection
   * @throws HttpNetworkErrorException if a network error occurs
   */
  public HttpResponse<String> send(HttpRequest request) {
    validateSsrfProtection(request.uri());

    try {
      log.debug("Sending request to: {}", request.uri());
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (java.net.http.HttpTimeoutException e) {
      log.warn("HTTP request timed out: {}", e.getMessage(), e);
      throw new HttpNetworkErrorException("HTTP request timed out", e);
    } catch (InterruptedException e) {
      // Restore the interrupt flag cleared by InterruptedException,
      // so that callers (e.g. ExecutorService shutdown) can detect the interruption.
      Thread.currentThread().interrupt();
      log.error("HTTP request interrupted: {}", e.getMessage(), e);
      throw new HttpNetworkErrorException("HTTP request interrupted", e);
    } catch (IOException e) {
      log.error("HTTP request failed: {}", e.getMessage(), e);
      throw new HttpNetworkErrorException("HTTP request failed", e);
    }
  }

  /**
   * Returns the underlying HttpClient.
   *
   * @return the HTTP client
   */
  public HttpClient httpClient() {
    return httpClient;
  }

  private void validateSsrfProtection(URI uri) {
    if (systemConfigurationResolver == null) {
      log.warn("SystemConfigurationResolver not configured, SSRF protection disabled");
      return;
    }

    SystemConfiguration systemConfiguration = systemConfigurationResolver.resolve();
    if (systemConfiguration == null) {
      log.warn("SystemConfiguration not found, SSRF protection disabled");
      return;
    }

    SsrfProtectionConfig ssrfConfig = systemConfiguration.ssrf();
    if (ssrfConfig == null || !ssrfConfig.isEnabled()) {
      log.debug("SSRF protection is disabled");
      return;
    }

    String host = uri.getHost();
    if (host != null && ssrfConfig.isBypassHost(host)) {
      log.debug("SSRF bypass: host={} is in bypass list", host);
      return;
    }

    SsrfProtectionValidator validator =
        SsrfProtectionValidator.withBypassHosts(ssrfConfig.bypassHosts());

    if (ssrfConfig.hasAllowedHosts()) {
      validator.validateWithAllowlist(uri, ssrfConfig.allowedHosts());
    } else {
      validator.validate(uri);
    }
    log.debug("SSRF validation passed for URI: {}", uri);
  }
}
