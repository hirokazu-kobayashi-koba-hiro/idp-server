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

package org.idp.server.security.event.hook.ssf;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.configuration.SecurityEventConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventExecutionConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

public class SsfHookExecutor implements SecurityEventHook {

  LoggerWrapper log = LoggerWrapper.getLogger(SsfHookExecutor.class);
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public SsfHookExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.SSF.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    log.trace("SSF hook execution started: event_type={}", securityEvent.type().value());

    try {
      SecurityEventConfig securityEventConfig = hookConfiguration.getEvent(securityEvent.type());
      SharedSignalFrameworkMetadataConfig metadataConfig =
          jsonConverter.read(
              hookConfiguration.metadata(), SharedSignalFrameworkMetadataConfig.class);
      SecurityEventExecutionConfig executionConfig = securityEventConfig.execution();
      SharedSignalFrameworkTransmissionConfig transmissionConfig =
          jsonConverter.read(
              executionConfig.details(), SharedSignalFrameworkTransmissionConfig.class);

      SecurityEventTokenCreator securityEventTokenCreator =
          new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);
      SecurityEventToken securityEventToken = securityEventTokenCreator.create();

      log.trace("SSF token created, sending to endpoint: url={}", transmissionConfig.url());
      log.debug(
          "SSF transmission details: endpoint={}, oauth_enabled={}, token_length={}",
          transmissionConfig.url(),
          transmissionConfig.oauthAuthorization() != null,
          securityEventToken.value().length());

      return send(
          hookConfiguration,
          securityEvent,
          transmissionConfig.url(),
          securityEventToken,
          transmissionConfig,
          startTime);

    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      log.error("SSF hook execution failed: event_type={}", securityEvent.type().value(), e);

      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          e.getClass().getSimpleName(),
          "SSF hook execution failed: " + e.getMessage());
    }
  }

  private SecurityEventHookResult send(
      SecurityEventHookConfiguration hookConfiguration,
      SecurityEvent securityEvent,
      String endpoint,
      SecurityEventToken securityEventToken,
      SharedSignalFrameworkTransmissionConfig transmissionConfig,
      long startTime) {

    log.debug("send shared signal request url: {}, set: {}", endpoint, securityEventToken.value());

    try {
      // Build and execute SSF transmission request
      HttpRequest httpRequest = createSsfRequest(endpoint, securityEventToken);
      HttpRequestResult httpRequestResult = executeRequest(httpRequest, transmissionConfig);

      long executionDurationMs = System.currentTimeMillis() - startTime;
      log.trace(
          "SSF HTTP response received: status={}, endpoint={}",
          httpRequestResult.statusCode(),
          endpoint);

      // Create execution details for context
      Map<String, Object> executionDetails =
          createExecutionDetails(
              endpoint, securityEventToken, httpRequestResult, transmissionConfig);

      // Handle response status
      if (httpRequestResult.isClientError()) {
        log.warn(
            "SSF transmission client error: endpoint={}, status={}, response={}",
            endpoint,
            httpRequestResult.statusCode(),
            httpRequestResult.body());
        return SecurityEventHookResult.failureWithContext(
            hookConfiguration,
            securityEvent,
            executionDetails,
            executionDurationMs,
            "SSF_CLIENT_ERROR",
            "SSF transmission client error: " + httpRequestResult.statusCode());
      }

      if (httpRequestResult.isServerError()) {
        log.error(
            "SSF transmission server error: endpoint={}, status={}, response={}",
            endpoint,
            httpRequestResult.statusCode(),
            httpRequestResult.body());
        return SecurityEventHookResult.failureWithContext(
            hookConfiguration,
            securityEvent,
            executionDetails,
            executionDurationMs,
            "SSF_SERVER_ERROR",
            "SSF transmission server error: " + httpRequestResult.statusCode());
      }

      log.trace("SSF transmission successful: endpoint={}", endpoint);
      return SecurityEventHookResult.successWithContext(
          hookConfiguration, securityEvent, executionDetails, executionDurationMs);

    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      log.error("SSF transmission failed: endpoint={}, error={}", endpoint, e.getMessage(), e);
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          e.getClass().getSimpleName(),
          "SSF transmission failed: " + e.getMessage());
    }
  }

  private HttpRequest createSsfRequest(String endpoint, SecurityEventToken securityEventToken) {
    return HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Content-Type", "application/secevent+jwt")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(securityEventToken.value()))
        .build();
  }

  private HttpRequestResult executeRequest(
      HttpRequest httpRequest, SharedSignalFrameworkTransmissionConfig transmissionConfig) {
    if (transmissionConfig.oauthAuthorization() != null) {
      return httpRequestExecutor.executeWithOAuth(
          httpRequest, transmissionConfig.oauthAuthorization());
    } else {
      return httpRequestExecutor.execute(httpRequest);
    }
  }

  private Map<String, Object> createExecutionDetails(
      String endpoint,
      SecurityEventToken securityEventToken,
      HttpRequestResult httpRequestResult,
      SharedSignalFrameworkTransmissionConfig transmissionConfig) {
    Map<String, Object> executionDetails = new HashMap<>();

    // HTTP status code
    executionDetails.put("http_status_code", httpRequestResult.statusCode());

    // Request information
    Map<String, Object> request = new HashMap<>();
    request.put("endpoint", endpoint);
    request.put("method", "POST");
    request.put(
        "headers",
        Map.of("Content-Type", "application/secevent+jwt", "Accept", "application/json"));
    request.put("body", securityEventToken.value());
    request.put("body_size", securityEventToken.value().length());
    executionDetails.put("request", request);

    // Response information
    Map<String, Object> response = new HashMap<>();
    response.put("body", httpRequestResult.body().toMap());
    response.put("headers", httpRequestResult.headersAsSingleValueMap());
    executionDetails.put("response", response);

    // Hook-specific metadata
    Map<String, Object> hookMetadata = new HashMap<>();
    hookMetadata.put("oauth_authentication_used", transmissionConfig.oauthAuthorization() != null);
    hookMetadata.put("security_event_token_sent", true);
    hookMetadata.put(
        "security_event_token_hash", Integer.toHexString(securityEventToken.value().hashCode()));
    executionDetails.put("hook_metadata", hookMetadata);

    return executionDetails;
  }
}
