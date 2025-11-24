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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.SecurityEventHooks;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurations;
import org.idp.server.platform.security.log.SecurityEventLogService;
import org.idp.server.platform.security.repository.SecurityEventCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.platform.statistics.repository.DailyActiveUserCommandRepository;
import org.idp.server.platform.statistics.repository.TenantStatisticsCommandRepository;
import org.idp.server.platform.user.UserIdentifier;

public class SecurityEventHandler {

  SecurityEventCommandRepository securityEventCommandRepository;
  SecurityEventHookResultCommandRepository resultsCommandRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  SecurityEventLogService logService;
  TenantStatisticsCommandRepository statisticsRepository;
  DailyActiveUserCommandRepository dailyActiveUserRepository;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHandler.class);

  public SecurityEventHandler(
      SecurityEventHooks securityEventHooks,
      SecurityEventHookResultCommandRepository resultsCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      SecurityEventLogService logService,
      TenantStatisticsCommandRepository statisticsRepository,
      DailyActiveUserCommandRepository dailyActiveUserRepository) {
    this.securityEventHooks = securityEventHooks;
    this.resultsCommandRepository = resultsCommandRepository;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.logService = logService;
    this.statisticsRepository = statisticsRepository;
    this.dailyActiveUserRepository = dailyActiveUserRepository;
  }

  public void handle(Tenant tenant, SecurityEvent securityEvent) {

    logService.logEvent(tenant, securityEvent);

    // Update statistics synchronously (same transaction)
    updateStatistics(tenant, securityEvent);

    SecurityEventHookConfigurations securityEventHookConfigurations =
        securityEventHookConfigurationQueryRepository.find(tenant);

    List<SecurityEventHookResult> results = new ArrayList<>();
    for (SecurityEventHookConfiguration hookConfiguration : securityEventHookConfigurations) {

      SecurityEventHook securityEventHookExecutor =
          securityEventHooks.get(hookConfiguration.hookType());

      if (securityEventHookExecutor.shouldExecute(tenant, securityEvent, hookConfiguration)) {
        log.info(
            String.format(
                "security event hook execution trigger: %s, type: %s tenant: %s client: %s user: %s, ",
                securityEvent.type().value(),
                hookConfiguration.hookType().name(),
                securityEvent.tenantIdentifierValue(),
                securityEvent.clientIdentifierValue(),
                securityEvent.userSub()));

        SecurityEventHookResult hookResult =
            securityEventHookExecutor.execute(tenant, securityEvent, hookConfiguration);
        results.add(hookResult);
      }
    }

    if (!results.isEmpty()) {
      resultsCommandRepository.bulkRegister(tenant, results);
    }
  }

  /**
   * Update tenant statistics based on security event
   *
   * <p>Processes security events and incrementally updates daily statistics metrics such as DAU,
   * login counts, token issuance, etc.
   *
   * @param tenant the tenant
   * @param securityEvent the security event
   */
  private void updateStatistics(Tenant tenant, SecurityEvent securityEvent) {
    if (securityEvent == null) {
      return;
    }

    // Convert UTC timestamp to tenant's local date
    String eventType = securityEvent.type().value();
    LocalDate eventDate =
        securityEvent
            .createdAt()
            .value()
            .atZone(ZoneOffset.UTC)
            .withZoneSameInstant(tenant.timezone())
            .toLocalDate();

    if (eventType.equals("login_success")) {
      UserIdentifier userId =
          securityEvent.hasUser()
              ? new UserIdentifier(securityEvent.user().subAsUuid().toString())
              : null;
      handleLoginSuccess(tenant, userId, eventDate);
    } else {
      incrementMetric(tenant, eventDate, eventType);
    }
  }

  /**
   * Handle login success event
   *
   * <p>Increments login_success_count and tracks unique daily active users (DAU)
   */
  private void handleLoginSuccess(Tenant tenant, UserIdentifier userId, LocalDate eventDate) {
    if (userId == null) {
      return;
    }

    // Increment login success count
    incrementMetric(tenant, eventDate, "login_success_count");

    // Track DAU - add user to daily active users table and increment DAU count if new
    boolean isNewActiveUser =
        dailyActiveUserRepository.addActiveUserAndReturnIfNew(
            tenant.identifier(), eventDate, userId);

    if (isNewActiveUser) {
      log.debug(
          "New daily active user: tenant={}, date={}, user={}",
          tenant.identifierValue(),
          eventDate,
          userId.value());
      // Increment DAU count in tenant_statistics_data
      incrementMetric(tenant, eventDate, "dau");
    } else {
      log.debug(
          "User already active today: tenant={}, date={}, user={}",
          tenant.identifierValue(),
          eventDate,
          userId.value());
    }
  }

  /**
   * Increment a numeric metric by 1
   *
   * @param tenant the tenant
   * @param date statistics date
   * @param metricName metric name to increment
   */
  private void incrementMetric(Tenant tenant, LocalDate date, String metricName) {
    log.debug(
        "Incrementing metric: tenant={}, date={}, metric={}",
        tenant.identifierValue(),
        date,
        metricName);
    statisticsRepository.incrementMetric(tenant.identifier(), date, metricName, 1);
  }
}
