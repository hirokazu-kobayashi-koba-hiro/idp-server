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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.type.IpAddress;
import org.idp.server.platform.security.type.UserAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression test for Issue #1621.
 *
 * <p>The DATADOG_LOG hook configuration must be read from the unified hook schema ({@code
 * events.<type>.execution.http_request}), the same way the WEBHOOK hook is read. Previously the
 * executor deserialized the whole {@link SecurityEventHookConfiguration} into the legacy {@code
 * WebHookConfiguration} ({@code base}/{@code overlays}) structure that never matched the schema, so
 * {@code overlays} was always null and {@code execute()} threw a {@link NullPointerException} that
 * was swallowed into a failure result — the hook never succeeded.
 */
@ExtendWith(MockitoExtension.class)
class DatadogSecurityEventHookExecutorTest {

  @Mock HttpRequestExecutor httpRequestExecutor;
  @Mock Tenant tenant;

  DatadogSecurityEventHookExecutor executor;
  SecurityEvent securityEvent;

  @BeforeEach
  void setUp() {
    executor = new DatadogSecurityEventHookExecutor(httpRequestExecutor);

    SecurityEventTenant eventTenant =
        new SecurityEventTenant("tenant-id", "https://tenant.example.com", "Test Tenant");
    SecurityEventClient eventClient = new SecurityEventClient("client-id", "client-123");
    SecurityEventUser eventUser =
        new SecurityEventUser("user-123", "Test User", "user-ex-123", "test@example.com", null);
    SecurityEventDetail detail = new SecurityEventDetail(Map.of("key", "value"));

    securityEvent =
        new SecurityEventBuilder()
            .add(new SecurityEventType("user_signup"))
            .add(new SecurityEventDescription("User signed up"))
            .add(eventTenant)
            .add(eventClient)
            .add(eventUser)
            .add(new IpAddress("192.168.1.1"))
            .add(new UserAgent("Mozilla/5.0"))
            .add(detail)
            .build();
  }

  @Test
  void execute_shouldNotThrowAndDelegateToConfiguredUrl_withUnifiedSchema() {
    String url = "https://http-intake.logs.datadoghq.com/api/v2/logs";
    when(httpRequestExecutor.execute(
            any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class)))
        .thenReturn(successResult());

    SecurityEventHookConfiguration hookConfiguration =
        hookConfiguration(Map.of("default", datadogEvent(url)));

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isSuccess());
    ArgumentCaptor<HttpRequestExecutionConfig> captor =
        ArgumentCaptor.forClass(HttpRequestExecutionConfig.class);
    verify(httpRequestExecutor).execute(captor.capture(), any(HttpRequestBaseParams.class));
    assertEquals(url, captor.getValue().httpRequestUrl().value());
  }

  @Test
  void execute_shouldPreferEventSpecificConfigOverDefault() {
    String defaultUrl = "https://http-intake.logs.datadoghq.com/api/v2/logs/default";
    String signupUrl = "https://http-intake.logs.datadoghq.com/api/v2/logs/signup";
    when(httpRequestExecutor.execute(
            any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class)))
        .thenReturn(successResult());

    Map<String, Object> events = new HashMap<>();
    events.put("default", datadogEvent(defaultUrl));
    events.put("user_signup", datadogEvent(signupUrl));

    SecurityEventHookResult result =
        executor.execute(tenant, securityEvent, hookConfiguration(events));

    assertTrue(result.isSuccess());
    ArgumentCaptor<HttpRequestExecutionConfig> captor =
        ArgumentCaptor.forClass(HttpRequestExecutionConfig.class);
    verify(httpRequestExecutor).execute(captor.capture(), any(HttpRequestBaseParams.class));
    assertEquals(signupUrl, captor.getValue().httpRequestUrl().value());
  }

  @Test
  void execute_shouldReturnConfigurationError_whenUrlMissing() {
    Map<String, Object> event = Map.of("execution", Map.of("function", "http_request"));

    SecurityEventHookResult result =
        executor.execute(tenant, securityEvent, hookConfiguration(Map.of("default", event)));

    assertTrue(result.isFailure());
    verify(httpRequestExecutor, never())
        .execute(any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class));
  }

  @Test
  void execute_shouldReturnFailure_whenHttpRequestNonSuccess() {
    HttpRequestResult httpResult =
        new HttpRequestResult(
            403, Map.of(), JsonNodeWrapper.fromMap(Map.of("errors", "forbidden")));
    when(httpRequestExecutor.execute(
            any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class)))
        .thenReturn(httpResult);

    SecurityEventHookConfiguration hookConfiguration =
        hookConfiguration(
            Map.of("default", datadogEvent("https://http-intake.logs.datadoghq.com/api/v2/logs")));

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isFailure());
  }

  @Test
  void execute_shouldReturnFailure_whenHttpRequestThrows() {
    when(httpRequestExecutor.execute(
            any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class)))
        .thenThrow(new RuntimeException("Connection refused"));

    SecurityEventHookConfiguration hookConfiguration =
        hookConfiguration(
            Map.of("default", datadogEvent("https://http-intake.logs.datadoghq.com/api/v2/logs")));

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isFailure());
  }

  private static HttpRequestResult successResult() {
    return new HttpRequestResult(202, Map.of(), JsonNodeWrapper.fromMap(Map.of("status", "ok")));
  }

  private static Map<String, Object> datadogEvent(String url) {
    Map<String, Object> httpRequest = new HashMap<>();
    httpRequest.put("url", url);
    httpRequest.put("method", "POST");
    return Map.of("execution", Map.of("function", "http_request", "http_request", httpRequest));
  }

  private static SecurityEventHookConfiguration hookConfiguration(Map<String, Object> events) {
    Map<String, Object> config = new HashMap<>();
    config.put("id", "hook-1");
    config.put("type", "DATADOG_LOG");
    config.put("triggers", List.of("user_signup"));
    config.put("execution_order", 1);
    config.put("events", events);
    config.put("enabled", true);
    return JsonConverter.snakeCaseInstance().read(config, SecurityEventHookConfiguration.class);
  }
}
