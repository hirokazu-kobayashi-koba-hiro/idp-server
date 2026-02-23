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

import java.util.Map;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.configuration.SecurityEventConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventExecutionConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

/**
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8935">Push-Based Security Event Token
 *     (SET) Delivery Using HTTP</a>
 */
public class WebHookSecurityEventExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;

  public WebHookSecurityEventExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
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
      SecurityEventConfig securityEventConfig = hookConfiguration.getEvent(securityEvent.type());
      SecurityEventExecutionConfig executionConfig = securityEventConfig.execution();
      HttpRequestExecutionConfig httpRequestConfig = executionConfig.httpRequest();

      HttpRequestBaseParams params = new HttpRequestBaseParams(securityEvent.toMap());
      HttpRequestResult httpResult = httpRequestExecutor.execute(httpRequestConfig, params);
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
            "HTTP request failed with status: " + httpResult.statusCode());
      }

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
}
