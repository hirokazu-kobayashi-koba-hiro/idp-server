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
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
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

  public SsfHookExecutor(OAuthAuthorizationResolvers oAuthAuthorizationResolvers) {
    this.httpRequestExecutor =
        new HttpRequestExecutor(HttpClientFactory.defaultClient(), oAuthAuthorizationResolvers);
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

    log.trace("SSF hook execution started: event_type={}", securityEvent.type().value());

    SecurityEventConfig securityEventConfig = hookConfiguration.getEvent(securityEvent.type());
    SharedSignalFrameworkMetadataConfig metadataConfig =
        jsonConverter.read(hookConfiguration.metadata(), SharedSignalFrameworkMetadataConfig.class);
    SecurityEventExecutionConfig executionConfig = securityEventConfig.execution();
    SharedSignalFrameworkTransmissionConfig transmissionConfig =
        jsonConverter.read(
            executionConfig.details(), SharedSignalFrameworkTransmissionConfig.class);

    SecurityEventTokenCreator securityEventTokenCreator =
        new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);
    SecurityEventToken securityEventToken = securityEventTokenCreator.create();

    log.trace("SSF token created, sending to endpoint: url={}", transmissionConfig.url());

    return send(transmissionConfig.url(), securityEventToken, transmissionConfig);
  }

  private SecurityEventHookResult send(
      String endpoint,
      SecurityEventToken securityEventToken,
      SharedSignalFrameworkTransmissionConfig transmissionConfig) {

    log.debug("send shared signal request url: {}, set: {}", endpoint, securityEventToken.value());

    try {
      // Build HttpRequest for SSF transmission
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(endpoint))
              .header("Content-Type", "application/secevent+jwt")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(securityEventToken.value()));

      HttpRequest httpRequest = builder.build();

      // Execute HTTP request through HttpRequestExecutor with OAuth support if configured
      HttpRequestResult httpRequestResult;
      if (transmissionConfig.oauthAuthorization() != null) {
        httpRequestResult =
            httpRequestExecutor.executeWithOAuth(
                httpRequest, transmissionConfig.oauthAuthorization());
      } else {
        httpRequestResult = httpRequestExecutor.execute(httpRequest);
      }

      log.trace(
          "SSF HTTP response received: status={}, endpoint={}",
          httpRequestResult.statusCode(),
          endpoint);

      // Handle response based on status code
      String body = httpRequestResult.body().toString();
      if (httpRequestResult.isClientError()) {
        log.warn(
            "SSF transmission client error: endpoint={}, status={}, response={}",
            endpoint,
            httpRequestResult.statusCode(),
            body);
        Map<String, Object> response = new HashMap<>();
        response.put("message", body);
        return SecurityEventHookResult.failure(type(), response);
      }

      if (httpRequestResult.isServerError()) {
        log.error(
            "SSF transmission server error: endpoint={}, status={}, response={}",
            endpoint,
            httpRequestResult.statusCode(),
            body);
        Map<String, Object> response = new HashMap<>();
        response.put("message", body);
        return SecurityEventHookResult.failure(type(), response);
      }

      log.trace("SSF transmission successful: endpoint={}", endpoint);
      Map<String, Object> response = new HashMap<>();
      response.put("message", body);
      return SecurityEventHookResult.success(type(), response);

    } catch (Exception e) {
      log.error("SSF transmission failed: endpoint={}, error={}", endpoint, e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("message", e.getMessage());
      return SecurityEventHookResult.failure(type(), response);
    }
  }
}
