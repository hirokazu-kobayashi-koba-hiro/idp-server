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

package org.idp.server.security.event.hooks.slack;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.NotificationTemplateInterpolator;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

public class SlackSecurityEventHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public SlackSecurityEventHookExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookType type() {
    return new SecurityEventHookType("SLACK");
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    SlackSecurityEventHookConfiguration configuration =
        jsonConverter.read(hookConfiguration.events(), SlackSecurityEventHookConfiguration.class);
    String incomingWebhookUrl = configuration.incomingWebhookUrl(securityEvent.type());
    if (incomingWebhookUrl == null) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          "CONFIGURATION_ERROR",
          "Slack incoming webhook URL not configured for event type: "
              + securityEvent.type().value());
    }

    String template = configuration.messageTemplate(securityEvent.type());

    Map<String, Object> context = new HashMap<>(securityEvent.toMap());
    context.put("trigger", securityEvent.type().value());

    NotificationTemplateInterpolator notificationTemplateInterpolator =
        new NotificationTemplateInterpolator(template, context);
    String message = notificationTemplateInterpolator.interpolate();

    String jsonBody = "{\"text\": \"" + escapeJson(message) + "\"}";

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(incomingWebhookUrl))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
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
            "Slack request failed with status: " + httpResult.statusCode());
      }

    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          e.getClass().getSimpleName(),
          "Slack request failed: " + e.getMessage());
    }
  }

  private String escapeJson(String value) {
    return value.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
  }
}
