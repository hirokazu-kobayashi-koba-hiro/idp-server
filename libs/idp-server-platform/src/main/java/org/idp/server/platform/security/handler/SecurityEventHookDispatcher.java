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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHooks;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurations;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;

/**
 * Dispatches a {@link SecurityEvent} to every configured hook for the tenant.
 *
 * <p>Owns the hook-execution concern that used to live inline in {@link SecurityEventHandler}:
 * loading the hook configurations, resolving each executor, running it, and persisting the results.
 * Keeping it separate from the statistics concern lets this be unit-tested without the statistics
 * repositories.
 *
 * <p>Failures are isolated per hook (see {@link #executeHook}). Callers should invoke {@link
 * #dispatch} <em>before</em> taking the statistics row lock so that blocking hook I/O is not held
 * inside the lock window (#1442).
 */
public class SecurityEventHookDispatcher {

  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  SecurityEventHookResultCommandRepository resultsCommandRepository;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHookDispatcher.class);

  public SecurityEventHookDispatcher(
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      SecurityEventHookResultCommandRepository resultsCommandRepository) {
    this.securityEventHooks = securityEventHooks;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.resultsCommandRepository = resultsCommandRepository;
  }

  /**
   * Runs every configured hook for the event and persists the collected results.
   *
   * <p>Hook execution involves blocking I/O (email, Slack, webhook: 450-500ms), so this is intended
   * to run before the statistics update to minimize the statistics row lock hold time (#1442).
   */
  public void dispatch(Tenant tenant, SecurityEvent securityEvent) {

    SecurityEventHookConfigurations securityEventHookConfigurations =
        securityEventHookConfigurationQueryRepository.find(tenant);

    List<SecurityEventHookResult> results = new ArrayList<>();
    for (SecurityEventHookConfiguration hookConfiguration : securityEventHookConfigurations) {

      Optional<SecurityEventHook> optionalExecutor =
          securityEventHooks.find(hookConfiguration.hookType());

      if (optionalExecutor.isEmpty()) {
        log.warn(
            "Skipping unsupported security event hook type: {} for tenant: {}",
            hookConfiguration.hookType().name(),
            tenant.identifierValue());
        continue;
      }

      SecurityEventHook securityEventHookExecutor = optionalExecutor.get();

      if (securityEventHookExecutor.shouldExecute(tenant, securityEvent, hookConfiguration)) {
        log.info(
            String.format(
                "security event hook execution trigger: %s, type: %s tenant: %s client: %s user: %s, ",
                securityEvent.type().value(),
                hookConfiguration.hookType().name(),
                securityEvent.tenantIdentifierValue(),
                securityEvent.clientIdentifierValue(),
                securityEvent.userSub()));

        results.add(
            executeHook(tenant, securityEvent, hookConfiguration, securityEventHookExecutor));
      }
    }

    if (!results.isEmpty()) {
      resultsCommandRepository.bulkRegister(tenant, results);
    }
  }

  /**
   * Executes a single hook, isolating unexpected failures as a FAILURE result.
   *
   * <p>Each executor already converts the failures it can anticipate into a {@link
   * SecurityEventHookResult}, but exceptions thrown outside its own try/catch (configuration
   * parsing, template interpolation, NPE on a missing field — see #1447) would otherwise propagate
   * out of {@link #dispatch}, aborting the remaining hooks and the subsequent statistics update. In
   * the synchronous ({@code publishSync}) path that turns the whole authentication request into a
   * 500. Converting the failure here keeps it scoped to a single hook.
   *
   * <p>A {@code null} return is treated the same way: an executor is contractually required to
   * return a result, and a null would otherwise propagate into {@code bulkRegister} and abort the
   * rest of the processing with the very NPE this method exists to prevent.
   */
  private SecurityEventHookResult executeHook(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration,
      SecurityEventHook securityEventHookExecutor) {

    long hookStartTime = System.currentTimeMillis();
    try {
      SecurityEventHookResult hookResult =
          securityEventHookExecutor.execute(tenant, securityEvent, hookConfiguration);
      if (hookResult != null) {
        return hookResult;
      }
      log.error(
          "Security event hook execution returned null: type={} tenant={} event={}",
          hookConfiguration.hookType().name(),
          tenant.identifierValue(),
          securityEvent.type().value());
      return failureResult(
          securityEvent,
          hookConfiguration,
          hookStartTime,
          "NullHookResult",
          "Security event hook execution returned null");
    } catch (Exception e) {
      log.error(
          "Security event hook execution threw an unexpected exception: type={} tenant={} event={}",
          hookConfiguration.hookType().name(),
          tenant.identifierValue(),
          securityEvent.type().value(),
          e);
      return failureResult(
          securityEvent,
          hookConfiguration,
          hookStartTime,
          e.getClass().getSimpleName(),
          "Security event hook execution failed: " + e.getMessage());
    }
  }

  private SecurityEventHookResult failureResult(
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration,
      long hookStartTime,
      String errorType,
      String errorMessage) {
    long executionDurationMs = System.currentTimeMillis() - hookStartTime;
    return SecurityEventHookResult.failureWithContext(
        hookConfiguration, securityEvent, null, executionDurationMs, errorType, errorMessage);
  }
}
