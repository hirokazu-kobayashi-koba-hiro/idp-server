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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.http.HttpRequest;
import java.util.*;
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
 * Regression test for Issue #1447.
 *
 * <p>The SLACK hook configuration must be read from the unified hook schema ({@code
 * events.<type>.execution.details}), the same way EMAIL/WEBHOOK hooks are read. Previously the
 * executor deserialized the {@code events} map into a bespoke {@code base}/{@code overlays}
 * structure that never matched the documented schema, so {@code overlays} was always null and
 * {@code execute()} threw a {@link NullPointerException}. In the synchronous path that NPE aborted
 * the whole authentication request.
 */
@ExtendWith(MockitoExtension.class)
class SlackSecurityEventHookExecutorTest {

  @Mock HttpRequestExecutor httpRequestExecutor;
  @Mock Tenant tenant;

  SlackSecurityEventHookExecutor executor;
  SecurityEvent securityEvent;

  @BeforeEach
  void setUp() {
    executor = new SlackSecurityEventHookExecutor(httpRequestExecutor);

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
  void execute_shouldNotThrowAndPostToConfiguredUrl_withUnifiedSchema() {
    String webhookUrl = "https://hooks.slack.com/services/T000/B000/default";
    when(httpRequestExecutor.execute(any(HttpRequest.class))).thenReturn(successResult());

    SecurityEventHookConfiguration hookConfiguration =
        hookConfiguration(Map.of("default", slackEvent(webhookUrl, "🔐 ${trigger} / ${user.id}")));

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isSuccess());
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpRequestExecutor).execute(captor.capture());
    assertEquals(webhookUrl, captor.getValue().uri().toString());
  }

  @Test
  void execute_shouldPreferEventSpecificConfigOverDefault() {
    String defaultUrl = "https://hooks.slack.com/services/T000/B000/default";
    String signupUrl = "https://hooks.slack.com/services/T000/B000/signup";
    when(httpRequestExecutor.execute(any(HttpRequest.class))).thenReturn(successResult());

    Map<String, Object> events = new HashMap<>();
    events.put("default", slackEvent(defaultUrl, "default ${trigger}"));
    events.put("user_signup", slackEvent(signupUrl, "signup ${user.id}"));

    SecurityEventHookResult result =
        executor.execute(tenant, securityEvent, hookConfiguration(events));

    assertTrue(result.isSuccess());
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpRequestExecutor).execute(captor.capture());
    assertEquals(signupUrl, captor.getValue().uri().toString());
  }

  @Test
  void execute_shouldReturnConfigurationError_whenWebhookUrlMissing() {
    Map<String, Object> details = new HashMap<>();
    details.put("message_template", "no url here");
    Map<String, Object> event = Map.of("execution", Map.of("type", "slack", "details", details));

    SecurityEventHookResult result =
        executor.execute(tenant, securityEvent, hookConfiguration(Map.of("default", event)));

    assertTrue(result.isFailure());
    verify(httpRequestExecutor, never()).execute(any(HttpRequest.class));
  }

  @Test
  void execute_shouldReturnFailureWhenHttpRequestThrows() {
    when(httpRequestExecutor.execute(any(HttpRequest.class)))
        .thenThrow(new RuntimeException("Connection refused"));

    SecurityEventHookConfiguration hookConfiguration =
        hookConfiguration(
            Map.of(
                "default",
                slackEvent("https://hooks.slack.com/services/T000/B000/x", "${trigger}")));

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isFailure());
  }

  private static HttpRequestResult successResult() {
    return new HttpRequestResult(200, Map.of(), JsonNodeWrapper.fromMap(Map.of("ok", true)));
  }

  private static Map<String, Object> slackEvent(String incomingWebhookUrl, String messageTemplate) {
    Map<String, Object> details = new HashMap<>();
    details.put("description", "slack notification");
    details.put("incoming_webhook_url", incomingWebhookUrl);
    details.put("message_template", messageTemplate);
    return Map.of("execution", Map.of("type", "slack", "details", details));
  }

  private static SecurityEventHookConfiguration hookConfiguration(Map<String, Object> events) {
    Map<String, Object> config = new HashMap<>();
    config.put("id", "hook-1");
    config.put("type", "SLACK");
    config.put("triggers", List.of("user_signup"));
    config.put("execution_order", 1);
    config.put("events", events);
    config.put("enabled", true);
    return JsonConverter.snakeCaseInstance().read(config, SecurityEventHookConfiguration.class);
  }
}
