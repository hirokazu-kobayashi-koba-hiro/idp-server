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

package org.idp.server.security.event.hooks.datadog;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.security.event.hooks.webhook.WebHookConfiguration;

public class DatadogSecurityEventHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public DatadogSecurityEventHookExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookType type() {
    return new SecurityEventHookType("DATADOG_LOG");
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    try {
      WebHookConfiguration configuration =
          jsonConverter.read(hookConfiguration, WebHookConfiguration.class);
      HttpRequestUrl httpRequestUrl = configuration.httpRequestUrl(securityEvent.type());
      HttpRequestStaticHeaders httpRequestStaticHeaders =
          configuration.httpRequestHeaders(securityEvent.type());
      HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys =
          configuration.httpRequestDynamicBodyKeys(securityEvent.type());
      HttpRequestStaticBody httpRequestStaticBody =
          configuration.httpRequestStaticBody(securityEvent.type());

      validate(httpRequestStaticHeaders);
      validate(httpRequestStaticBody);

      HttpRequestBodyCreator requestBodyCreator =
          new HttpRequestBodyCreator(
              new HttpRequestBaseParams(securityEvent.toMap()),
              httpRequestDynamicBodyKeys,
              httpRequestStaticBody);
      Map<String, Object> requestBodyMap = requestBodyCreator.create();

      String body = jsonConverter.write(requestBodyMap);

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(httpRequestUrl.value()))
              .header("Content-Type", "application/json")
              .header("DD-API-KEY", httpRequestStaticHeaders.getValueAsString("DD-API-KEY"))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();

      HttpRequestResult httpResult = httpRequestExecutor.execute(httpRequest);
      long executionDurationMs = System.currentTimeMillis() - startTime;

      Map<String, Object> responseBody = httpResult.toMap();

      if (httpResult.isSuccess()) {
        return SecurityEventHookResult.successWithContext(
            hookConfiguration, securityEvent, responseBody, executionDurationMs);
      } else {
        return SecurityEventHookResult.failureWithContext(
            hookConfiguration,
            securityEvent,
            responseBody,
            executionDurationMs,
            "HTTP_ERROR",
            "Datadog log stream failed with status: " + httpResult.statusCode());
      }

    } catch (DatadogConfigurationInvalidException e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          "DATADOG_CONFIGURATION_ERROR",
          "Datadog configuration invalid: " + e.getMessage());
    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          e.getClass().getSimpleName(),
          "Datadog log stream failed: " + e.getMessage());
    }
  }

  private void validate(HttpRequestStaticHeaders httpRequestStaticHeaders) {
    if (!httpRequestStaticHeaders.containsKey("DD-API-KEY")) {
      throw new DatadogConfigurationInvalidException("DD-API-KEY header is required.");
    }
  }

  private void validate(HttpRequestStaticBody httpRequestStaticBody) {

    if (!httpRequestStaticBody.containsKey("ddsource")) {
      throw new DatadogConfigurationInvalidException("static body ddsource is required.");
    }
    if (!httpRequestStaticBody.containsKey("ddtags")) {
      throw new DatadogConfigurationInvalidException("static body ddtags is required.");
    }
    if (!httpRequestStaticBody.containsKey("ddsource")) {
      throw new DatadogConfigurationInvalidException("static body ddsource is required.");
    }
    if (!httpRequestStaticBody.containsKey("service")) {
      throw new DatadogConfigurationInvalidException("static body service is required.");
    }
  }
}
