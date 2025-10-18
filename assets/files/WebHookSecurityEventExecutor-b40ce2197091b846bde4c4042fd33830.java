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

package org.idp.server.security.event.hooks.webhook;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

/**
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8935">Push-Based Security Event Token
 *     (SET) Delivery Using HTTP</a>
 */
public class WebHookSecurityEventExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public WebHookSecurityEventExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.WEBHOOK.toHookType();
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
      HttpMethod httpMethod = configuration.httpMethod(securityEvent.type());
      HttpRequestStaticHeaders httpRequestStaticHeaders =
          configuration.httpRequestHeaders(securityEvent.type());
      HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys =
          configuration.httpRequestDynamicBodyKeys(securityEvent.type());
      HttpRequestStaticBody httpRequestStaticBody =
          configuration.httpRequestStaticBody(securityEvent.type());

      HttpRequestBodyCreator requestBodyCreator =
          new HttpRequestBodyCreator(
              new HttpRequestBaseParams(securityEvent.toMap()),
              httpRequestDynamicBodyKeys,
              httpRequestStaticBody);
      Map<String, Object> requestBody = requestBodyCreator.create();

      HttpRequest.Builder httpRequestBuilder =
          HttpRequest.newBuilder()
              .uri(new URI(httpRequestUrl.value()))
              .header("Content-Type", "application/json");

      setHeaders(httpRequestBuilder, httpRequestStaticHeaders);
      setParams(httpRequestBuilder, httpMethod, requestBody);

      HttpRequest httpRequest = httpRequestBuilder.build();

      HttpRequestResult httpResult = httpRequestExecutor.execute(httpRequest);
      long executionDurationMs = System.currentTimeMillis() - startTime;

      // Parse response body if it's JSON
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
            "HTTP request failed with status: " + httpResult.statusCode());
      }

    } catch (URISyntaxException e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          "URISyntaxException",
          "WebhookUrl is invalid: " + e.getMessage());
    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          e.getClass().getSimpleName(),
          "Webhook request failed: " + e.getMessage());
    }
  }

  private void setHeaders(
      HttpRequest.Builder httpRequestBuilder, HttpRequestStaticHeaders httpRequestStaticHeaders) {
    httpRequestStaticHeaders.forEach(httpRequestBuilder::header);
  }

  private void setParams(
      HttpRequest.Builder builder, HttpMethod httpMethod, Map<String, Object> requestBody) {

    switch (httpMethod) {
      case GET:
        builder.GET();
        break;
      case POST:
        {
          builder.POST(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          break;
        }
      case PUT:
        {
          builder.PUT(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          break;
        }
      case DELETE:
        {
          builder.DELETE();
          break;
        }
    }
  }
}
