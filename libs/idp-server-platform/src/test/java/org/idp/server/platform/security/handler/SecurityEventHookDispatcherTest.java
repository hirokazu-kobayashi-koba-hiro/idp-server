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

package org.idp.server.platform.security.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.SecurityEventType;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookType;
import org.idp.server.platform.security.hook.SecurityEventHooks;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurations;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * #1619: an executor that throws an unexpected exception (outside its own try/catch) must not
 * propagate out of {@link SecurityEventHookDispatcher#dispatch}. The exception is converted to a
 * FAILURE result and the remaining hooks still run, so one misconfigured/buggy hook cannot abort
 * the rest of the event processing — and, on the synchronous path, cannot turn the authentication
 * request into a 500.
 */
@ExtendWith(MockitoExtension.class)
class SecurityEventHookDispatcherTest {

  @Mock Tenant tenant;
  @Mock SecurityEvent securityEvent;
  @Mock SecurityEventHookConfigurationQueryRepository queryRepository;
  @Mock SecurityEventHookResultCommandRepository resultsCommandRepository;

  @Captor ArgumentCaptor<List<SecurityEventHookResult>> resultsCaptor;

  private void stubEventLogging() {
    lenient().when(tenant.identifierValue()).thenReturn("tenant-1");
    lenient().when(securityEvent.type()).thenReturn(new SecurityEventType("login_success"));
    lenient().when(securityEvent.tenantIdentifierValue()).thenReturn("tenant-1");
    lenient().when(securityEvent.clientIdentifierValue()).thenReturn("client-1");
    lenient().when(securityEvent.userSub()).thenReturn("user-1");
  }

  private SecurityEventHookConfiguration mockConfiguration(SecurityEventHookType type, String id) {
    SecurityEventHookConfiguration configuration = mock(SecurityEventHookConfiguration.class);
    lenient().when(configuration.hookType()).thenReturn(type);
    lenient()
        .when(configuration.identifier())
        .thenReturn(new SecurityEventHookConfigurationIdentifier(id));
    return configuration;
  }

  private SecurityEventHook mockExecutor() {
    SecurityEventHook executor = mock(SecurityEventHook.class);
    when(executor.shouldExecute(any(), any(), any())).thenReturn(true);
    return executor;
  }

  @Test
  void unexpectedException_isIsolated_andSubsequentHooksStillRun() {
    stubEventLogging();

    SecurityEventHookType webhookType = new SecurityEventHookType("WEBHOOK");
    SecurityEventHookType slackType = new SecurityEventHookType("SLACK");
    SecurityEventHookConfiguration webhookConfig = mockConfiguration(webhookType, "hook-webhook");
    SecurityEventHookConfiguration slackConfig = mockConfiguration(slackType, "hook-slack");
    when(queryRepository.find(tenant))
        .thenReturn(new SecurityEventHookConfigurations(List.of(webhookConfig, slackConfig)));

    SecurityEventHook throwingExecutor = mockExecutor();
    when(throwingExecutor.execute(any(), any(), any()))
        .thenThrow(new IllegalStateException("boom"));

    SecurityEventHook succeedingExecutor = mockExecutor();
    SecurityEventHookResult successResult =
        SecurityEventHookResult.successWithContext(slackConfig, securityEvent, Map.of(), 1L);
    when(succeedingExecutor.execute(any(), any(), any())).thenReturn(successResult);

    SecurityEventHooks hooks =
        new SecurityEventHooks(
            Map.of(webhookType, throwingExecutor, slackType, succeedingExecutor));
    SecurityEventHookDispatcher dispatcher =
        new SecurityEventHookDispatcher(hooks, queryRepository, resultsCommandRepository);

    assertDoesNotThrow(() -> dispatcher.dispatch(tenant, securityEvent));

    // The second hook ran despite the first one throwing.
    verify(succeedingExecutor).execute(any(), any(), any());

    verify(resultsCommandRepository).bulkRegister(eq(tenant), resultsCaptor.capture());
    List<SecurityEventHookResult> persisted = resultsCaptor.getValue();
    assertEquals(2, persisted.size());
    assertTrue(persisted.get(0).isFailure(), "throwing hook recorded as FAILURE");
    assertTrue(persisted.get(1).isSuccess(), "subsequent hook recorded as SUCCESS");
  }

  @Test
  void unexpectedException_isConvertedToFailureResult_withExceptionType() {
    stubEventLogging();

    SecurityEventHookType webhookType = new SecurityEventHookType("WEBHOOK");
    SecurityEventHookConfiguration webhookConfig = mockConfiguration(webhookType, "hook-webhook");
    when(queryRepository.find(tenant))
        .thenReturn(new SecurityEventHookConfigurations(List.of(webhookConfig)));

    SecurityEventHook throwingExecutor = mockExecutor();
    when(throwingExecutor.execute(any(), any(), any()))
        .thenThrow(new NullPointerException("overlays is null"));

    SecurityEventHooks hooks = new SecurityEventHooks(Map.of(webhookType, throwingExecutor));
    SecurityEventHookDispatcher dispatcher =
        new SecurityEventHookDispatcher(hooks, queryRepository, resultsCommandRepository);

    dispatcher.dispatch(tenant, securityEvent);

    verify(resultsCommandRepository).bulkRegister(eq(tenant), resultsCaptor.capture());
    SecurityEventHookResult result = resultsCaptor.getValue().get(0);
    assertTrue(result.isFailure());
    assertEquals("WEBHOOK", result.type().name());
  }

  @Test
  void nullHookResult_isConvertedToFailure() {
    stubEventLogging();

    SecurityEventHookType webhookType = new SecurityEventHookType("WEBHOOK");
    SecurityEventHookConfiguration webhookConfig = mockConfiguration(webhookType, "hook-webhook");
    when(queryRepository.find(tenant))
        .thenReturn(new SecurityEventHookConfigurations(List.of(webhookConfig)));

    // A misbehaving executor that returns null instead of a result must not NPE bulkRegister.
    SecurityEventHook nullReturningExecutor = mockExecutor();
    when(nullReturningExecutor.execute(any(), any(), any())).thenReturn(null);

    SecurityEventHooks hooks = new SecurityEventHooks(Map.of(webhookType, nullReturningExecutor));
    SecurityEventHookDispatcher dispatcher =
        new SecurityEventHookDispatcher(hooks, queryRepository, resultsCommandRepository);

    assertDoesNotThrow(() -> dispatcher.dispatch(tenant, securityEvent));

    verify(resultsCommandRepository).bulkRegister(eq(tenant), resultsCaptor.capture());
    SecurityEventHookResult result = resultsCaptor.getValue().get(0);
    assertTrue(result.isFailure());
    assertEquals("WEBHOOK", result.type().name());
  }

  @Test
  void unsupportedHookType_isSkipped_withoutPersistingResults() {
    SecurityEventHookType unknownType = new SecurityEventHookType("UNKNOWN");
    SecurityEventHookConfiguration unknownConfig = mockConfiguration(unknownType, "hook-unknown");
    when(queryRepository.find(tenant))
        .thenReturn(new SecurityEventHookConfigurations(List.of(unknownConfig)));

    // No executor registered for the configured type.
    SecurityEventHooks hooks = new SecurityEventHooks(Map.of());
    SecurityEventHookDispatcher dispatcher =
        new SecurityEventHookDispatcher(hooks, queryRepository, resultsCommandRepository);

    assertDoesNotThrow(() -> dispatcher.dispatch(tenant, securityEvent));

    verify(resultsCommandRepository, never()).bulkRegister(any(), any());
  }
}
