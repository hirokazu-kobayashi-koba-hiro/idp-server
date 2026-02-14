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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookType;
import org.idp.server.platform.security.hook.StandardSecurityEventHookType;
import org.idp.server.platform.security.hook.configuration.SecurityEventConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.type.IpAddress;
import org.idp.server.platform.security.type.UserAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebHookSecurityEventExecutorTest {

  @Mock HttpRequestExecutor httpRequestExecutor;
  @Mock Tenant tenant;

  WebHookSecurityEventExecutor executor;
  SecurityEvent securityEvent;

  @BeforeEach
  void setUp() {
    executor = new WebHookSecurityEventExecutor(httpRequestExecutor);

    SecurityEventTenant eventTenant =
        new SecurityEventTenant("tenant-id", "https://tenant.example.com", "Test Tenant");
    SecurityEventClient eventClient = new SecurityEventClient("client-id", "client-123");
    SecurityEventUser eventUser =
        new SecurityEventUser("user-123", "Test User", "user-ex-123", "test@example.com", null);
    SecurityEventDetail detail = new SecurityEventDetail(Map.of("key", "value"));

    securityEvent =
        new SecurityEventBuilder()
            .add(new SecurityEventType("authentication_success"))
            .add(new SecurityEventDescription("User authenticated"))
            .add(eventTenant)
            .add(eventClient)
            .add(eventUser)
            .add(new IpAddress("192.168.1.1"))
            .add(new UserAgent("Mozilla/5.0"))
            .add(detail)
            .build();
  }

  @Test
  void type_shouldReturnWebhookType() {
    SecurityEventHookType hookType = executor.type();
    assertEquals(StandardSecurityEventHookType.WEBHOOK.toHookType(), hookType);
  }

  @Test
  void execute_shouldReturnSuccessWhenHttpRequestSucceeds() {
    HttpRequestResult httpResult =
        new HttpRequestResult(200, Map.of(), JsonNodeWrapper.fromMap(Map.of("ok", true)));
    when(httpRequestExecutor.execute(
            any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class)))
        .thenReturn(httpResult);

    SecurityEventHookConfiguration hookConfiguration =
        createHookConfiguration("authentication_success");

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isSuccess());
    verify(httpRequestExecutor)
        .execute(any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class));
  }

  @Test
  void execute_shouldReturnFailureWhenHttpRequestFails() {
    HttpRequestResult httpResult =
        new HttpRequestResult(500, Map.of(), JsonNodeWrapper.fromMap(Map.of("error", "internal")));
    when(httpRequestExecutor.execute(
            any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class)))
        .thenReturn(httpResult);

    SecurityEventHookConfiguration hookConfiguration =
        createHookConfiguration("authentication_success");

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isFailure());
  }

  @Test
  void execute_shouldReturnFailureWhenExceptionThrown() {
    when(httpRequestExecutor.execute(
            any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class)))
        .thenThrow(new RuntimeException("Connection refused"));

    SecurityEventHookConfiguration hookConfiguration =
        createHookConfiguration("authentication_success");

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isFailure());
  }

  @Test
  void execute_shouldUseDefaultEventConfigWhenSpecificEventNotConfigured() {
    HttpRequestResult httpResult =
        new HttpRequestResult(200, Map.of(), JsonNodeWrapper.fromMap(Map.of("ok", true)));
    when(httpRequestExecutor.execute(
            any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class)))
        .thenReturn(httpResult);

    SecurityEventHookConfiguration hookConfiguration = createHookConfigurationWithDefaultOnly();

    SecurityEventHookResult result = executor.execute(tenant, securityEvent, hookConfiguration);

    assertTrue(result.isSuccess());
    verify(httpRequestExecutor)
        .execute(any(HttpRequestExecutionConfig.class), any(HttpRequestBaseParams.class));
  }

  private SecurityEventHookConfiguration createHookConfiguration(String eventType) {
    SecurityEventConfig securityEventConfig = new SecurityEventConfig();

    Map<String, SecurityEventConfig> events = new HashMap<>();
    events.put(eventType, securityEventConfig);

    return new SecurityEventHookConfiguration(
        "hook-1",
        "WEBHOOK",
        new HashMap<>(),
        new HashMap<>(),
        List.of(eventType),
        100,
        events,
        true,
        true);
  }

  private SecurityEventHookConfiguration createHookConfigurationWithDefaultOnly() {
    SecurityEventConfig securityEventConfig = new SecurityEventConfig();

    Map<String, SecurityEventConfig> events = new HashMap<>();
    events.put("default", securityEventConfig);

    return new SecurityEventHookConfiguration(
        "hook-1",
        "WEBHOOK",
        new HashMap<>(),
        new HashMap<>(),
        List.of("authentication_success"),
        100,
        events,
        true,
        true);
  }
}
