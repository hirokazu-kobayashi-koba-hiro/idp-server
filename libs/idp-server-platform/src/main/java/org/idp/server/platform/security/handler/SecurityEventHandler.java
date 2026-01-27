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
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventUser;
import org.idp.server.platform.security.event.SecurityEventUserIdentifier;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.SecurityEventHooks;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurations;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogService;
import org.idp.server.platform.security.repository.SecurityEventCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.platform.statistics.FiscalYearCalculator;
import org.idp.server.platform.statistics.repository.DailyActiveUserCommandRepository;
import org.idp.server.platform.statistics.repository.MonthlyActiveUserCommandRepository;
import org.idp.server.platform.statistics.repository.StatisticsEventsCommandRepository;
import org.idp.server.platform.statistics.repository.YearlyActiveUserCommandRepository;

public class SecurityEventHandler {

  SecurityEventCommandRepository securityEventCommandRepository;
  SecurityEventHookResultCommandRepository resultsCommandRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  SecurityEventLogService logService;
  StatisticsEventsCommandRepository statisticsEventsRepository;
  DailyActiveUserCommandRepository dailyActiveUserRepository;
  MonthlyActiveUserCommandRepository monthlyActiveUserRepository;
  YearlyActiveUserCommandRepository yearlyActiveUserRepository;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHandler.class);

  public SecurityEventHandler(
      SecurityEventHooks securityEventHooks,
      SecurityEventHookResultCommandRepository resultsCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      SecurityEventLogService logService,
      StatisticsEventsCommandRepository statisticsEventsRepository,
      DailyActiveUserCommandRepository dailyActiveUserRepository,
      MonthlyActiveUserCommandRepository monthlyActiveUserRepository,
      YearlyActiveUserCommandRepository yearlyActiveUserRepository) {
    this.securityEventHooks = securityEventHooks;
    this.resultsCommandRepository = resultsCommandRepository;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.logService = logService;
    this.statisticsEventsRepository = statisticsEventsRepository;
    this.dailyActiveUserRepository = dailyActiveUserRepository;
    this.monthlyActiveUserRepository = monthlyActiveUserRepository;
    this.yearlyActiveUserRepository = yearlyActiveUserRepository;
  }

  public void handle(Tenant tenant, SecurityEvent securityEvent) {

    logService.logEvent(tenant, securityEvent);

    // Update statistics synchronously (same transaction)
    SecurityEventLogConfiguration config = tenant.securityEventLogConfiguration();
    if (config.isStatisticsEnabled()) {
      updateStatistics(tenant, securityEvent);
    }

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
   * <p>Processes security events and incrementally updates statistics in the statistics_events
   * table. Tracks DAU, MAU, YAU, login counts, token issuance, etc.
   *
   * @param tenant the tenant
   * @param securityEvent the security event
   */
  private void updateStatistics(Tenant tenant, SecurityEvent securityEvent) {
    if (securityEvent == null) {
      return;
    }

    // Convert UTC timestamp to tenant's local date
    String eventTypeValue = securityEvent.type().value();
    LocalDate eventDate =
        securityEvent
            .createdAt()
            .value()
            .atZone(ZoneOffset.UTC)
            .withZoneSameInstant(tenant.timezone())
            .toLocalDate();

    DefaultSecurityEventType eventType = DefaultSecurityEventType.findByValue(eventTypeValue);

    if (eventType != null && eventType.isActiveUserEvent() && securityEvent.hasUser()) {
      handleActiveUserEvent(tenant, securityEvent.user(), eventDate, eventTypeValue);
    } else {
      incrementMetric(tenant, eventDate, eventTypeValue);
    }
  }

  /**
   * Handle active user event
   *
   * <p>Increments the event type metric (e.g., login_success, issue_token_success) and tracks
   * unique daily/monthly/yearly active users (DAU/MAU/YAU). An active user event is defined by
   * {@link DefaultSecurityEventType#isActiveUserEvent()}.
   */
  private void handleActiveUserEvent(
      Tenant tenant, SecurityEventUser securityEventUser, LocalDate eventDate, String eventType) {

    SecurityEventUserIdentifier userId = securityEventUser.securityEventUserIdentifier();
    String userName = securityEventUser.name();

    // Derive month and year from eventDate for statistics grouping
    LocalDate monthStart = eventDate.withDayOfMonth(1);
    LocalDate yearStart =
        FiscalYearCalculator.calculateFiscalYearStart(eventDate, tenant.fiscalYearStartMonth());

    // Increment the actual event type metric
    incrementMetric(tenant, eventDate, eventType);

    // Track DAU - add user to daily active users table and increment DAU count if new
    boolean isNewDailyUser =
        dailyActiveUserRepository.addActiveUserAndReturnIfNew(
            tenant.identifier(), eventDate, userId, userName);

    if (isNewDailyUser) {
      log.debug(
          "New daily active user: tenant={}, date={}, user={}",
          tenant.identifierValue(),
          eventDate,
          userId.value());
      statisticsEventsRepository.increment(tenant.identifier(), eventDate, "dau");
    } else {
      log.debug(
          "User already active today: tenant={}, date={}, user={}",
          tenant.identifierValue(),
          eventDate,
          userId.value());
    }

    // Track MAU - add user to monthly active users table and increment MAU count if new
    boolean isNewMonthlyUser =
        monthlyActiveUserRepository.addActiveUserAndReturnIfNew(
            tenant.identifier(), monthStart, userId, userName);

    if (isNewMonthlyUser) {
      log.debug(
          "New monthly active user: tenant={}, month={}, user={}",
          tenant.identifierValue(),
          monthStart,
          userId.value());
      statisticsEventsRepository.increment(tenant.identifier(), eventDate, "mau");
    } else {
      log.debug(
          "User already active this month: tenant={}, month={}, user={}",
          tenant.identifierValue(),
          monthStart,
          userId.value());
    }

    // Track YAU - add user to yearly active users table and increment YAU count if new
    boolean isNewYearlyUser =
        yearlyActiveUserRepository.addActiveUserAndReturnIfNew(
            tenant.identifier(), yearStart, userId, userName);

    if (isNewYearlyUser) {
      log.debug(
          "New yearly active user: tenant={}, year={}, user={}",
          tenant.identifierValue(),
          yearStart,
          userId.value());
      statisticsEventsRepository.increment(tenant.identifier(), eventDate, "yau");
    } else {
      log.debug(
          "User already active this year: tenant={}, year={}, user={}",
          tenant.identifierValue(),
          yearStart,
          userId.value());
    }
  }

  /**
   * Increment a metric in the statistics_events table
   *
   * @param tenant the tenant
   * @param date statistics date
   * @param eventType event type to increment
   */
  private void incrementMetric(Tenant tenant, LocalDate date, String eventType) {
    log.debug(
        "Incrementing metric: tenant={}, date={}, eventType={}",
        tenant.identifierValue(),
        date,
        eventType);

    statisticsEventsRepository.increment(tenant.identifier(), date, eventType);
  }
}
