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
 * Streams security events to <a
 * href="https://docs.datadoghq.com/api/latest/logs/#send-logs">Datadog Logs Intake</a> using the
 * unified hook schema ({@code events.<type>.execution.http_request}) — the same shape used by the
 * {@code WEBHOOK} hook ({@link
 * org.idp.server.security.event.hooks.webhook.WebHookSecurityEventExecutor}).
 *
 * <p>There are no Datadog-specific configuration fields. Everything is expressed through the
 * generic {@link HttpRequestExecutionConfig}:
 *
 * <ul>
 *   <li>{@code DD-API-KEY} — set via {@code http_request.header_mapping_rules} (with {@code to:
 *       "DD-API-KEY"})
 *   <li>{@code ddsource} / {@code ddtags} / {@code service} / {@code message} — set via {@code
 *       http_request.body_mapping_rules}
 * </ul>
 *
 * The security event is exposed to the mapping rules as the request source ({@link
 * org.idp.server.platform.security.SecurityEvent#toMap()}), and {@code Content-Type:
 * application/json} is applied automatically by {@link
 * org.idp.server.platform.http.HttpRequestBuilder} unless overridden.
 *
 * <p>Behavior:
 *
 * <ul>
 *   <li>{@code http_request.url} missing → failure result with error type {@code
 *       DATADOG_CONFIGURATION_ERROR}; no HTTP call is performed.
 *   <li>non-2xx responses and network/runtime errors → failure result; never propagated to the
 *       caller.
 * </ul>
 *
 * <p>Prior to the unified-schema migration this executor read a bespoke {@code base}/{@code
 * overlays} structure and validated {@code DD-API-KEY}/{@code ddsource}/{@code ddtags}/{@code
 * service} presence; that validation was removed because those values are now operator-supplied
 * mapping rules. A misconfigured key surfaces as a Datadog 4xx ({@code HTTP_ERROR} failure), not a
 * silent drop.
 */
public class DatadogSecurityEventHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;

  public DatadogSecurityEventHookExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
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
      SecurityEventConfig securityEventConfig = hookConfiguration.getEvent(securityEvent.type());
      SecurityEventExecutionConfig executionConfig = securityEventConfig.execution();
      HttpRequestExecutionConfig httpRequestConfig = executionConfig.httpRequest();

      if (!httpRequestConfig.exists()) {
        throw new DatadogConfigurationInvalidException(
            "http_request url is not configured for event type: " + securityEvent.type().value());
      }

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
}
