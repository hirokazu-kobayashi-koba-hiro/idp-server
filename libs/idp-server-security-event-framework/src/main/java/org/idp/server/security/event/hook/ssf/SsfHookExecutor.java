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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.http.HttpClientFactory;
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
  HttpClient httpClient;
  JsonConverter jsonConverter;

  public SsfHookExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
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

    return send(
        new SharedSignalEventRequest(transmissionConfig.url(), Map.of(), securityEventToken));
  }

  private SecurityEventHookResult send(SharedSignalEventRequest sharedSignalEventRequest) {
    try {

      log.debug(
          "send shared signal request url: {}, set: {}",
          sharedSignalEventRequest.endpoint(),
          sharedSignalEventRequest.securityEventTokenValue());

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(sharedSignalEventRequest.endpoint()))
              .header("Content-Type", "application/secevent+jwt")
              .header("Accept", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      sharedSignalEventRequest.securityEventTokenValue()));

      HttpRequest request = builder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();
      if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() < 500) {

        log.warn("ssf response:" + body);
        Map<String, Object> response = new HashMap<>();
        response.put("message", body);
        return SecurityEventHookResult.failure(type(), response);
      }

      if (httpResponse.statusCode() >= 500) {
        log.error("ssf response:" + body);
        Map<String, Object> response = new HashMap<>();
        response.put("message", body);
        return SecurityEventHookResult.failure(type(), response);
      }

      Map<String, Object> response = new HashMap<>();
      response.put("message", body);
      return SecurityEventHookResult.success(type(), response);

    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error(e.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("message", e.getMessage());
      return SecurityEventHookResult.failure(type(), response);
    }
  }
}
