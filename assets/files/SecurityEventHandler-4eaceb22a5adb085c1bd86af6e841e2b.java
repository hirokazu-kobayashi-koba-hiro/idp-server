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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
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
import org.idp.server.platform.statistics.repository.MonthlyActiveUserCommandRepository;
import org.idp.server.platform.statistics.repository.TenantStatisticsCommandRepository;
import org.idp.server.platform.statistics.repository.TenantYearlyStatisticsCommandRepository;
import org.idp.server.platform.statistics.repository.YearlyActiveUserCommandRepository;
import org.idp.server.platform.user.UserIdentifier;

public class SecurityEventHandler {

  private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  SecurityEventCommandRepository securityEventCommandRepository;
  SecurityEventHookResultCommandRepository resultsCommandRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  SecurityEventLogService logService;
  TenantStatisticsCommandRepository statisticsRepository;
  TenantYearlyStatisticsCommandRepository yearlyStatisticsRepository;
  DailyActiveUserCommandRepository dailyActiveUserRepository;
  MonthlyActiveUserCommandRepository monthlyActiveUserRepository;
  YearlyActiveUserCommandRepository yearlyActiveUserRepository;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHandler.class);

  public SecurityEventHandler(
      SecurityEventHooks securityEventHooks,
      SecurityEventHookResultCommandRepository resultsCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      SecurityEventLogService logService,
      TenantStatisticsCommandRepository statisticsRepository,
      TenantYearlyStatisticsCommandRepository yearlyStatisticsRepository,
      DailyActiveUserCommandRepository dailyActiveUserRepository,
      MonthlyActiveUserCommandRepository monthlyActiveUserRepository,
      YearlyActiveUserCommandRepository yearlyActiveUserRepository) {
    this.securityEventHooks = securityEventHooks;
    this.resultsCommandRepository = resultsCommandRepository;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.logService = logService;
    this.statisticsRepository = statisticsRepository;
    this.yearlyStatisticsRepository = yearlyStatisticsRepository;
    this.dailyActiveUserRepository = dailyActiveUserRepository;
    this.monthlyActiveUserRepository = monthlyActiveUserRepository;
    this.yearlyActiveUserRepository = yearlyActiveUserRepository;
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
   * <p>Processes security events and incrementally updates daily/monthly/yearly statistics metrics
   * such as DAU, MAU, YAU, login counts, token issuance, etc.
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
    String day = eventDate.format(DATE_FORMATTER);
    String statMonth = eventDate.format(MONTH_FORMATTER);
    String statYear = eventDate.format(YEAR_FORMATTER);

    if (eventType != null && eventType.isActiveUserEvent()) {
      UserIdentifier userId =
          securityEvent.hasUser()
              ? new UserIdentifier(securityEvent.user().subAsUuid().toString())
              : null;
      handleActiveUserEvent(tenant, userId, eventDate, day, statMonth, statYear);
    } else {
      incrementMetric(tenant, eventDate, eventTypeValue);
    }
  }

  /**
   * Handle active user event
   *
   * <p>Increments login_success_count and tracks unique daily/monthly/yearly active users
   * (DAU/MAU/YAU). An active user event is defined by {@link
   * DefaultSecurityEventType#isActiveUserEvent()}.
   */
  private void handleActiveUserEvent(
      Tenant tenant,
      UserIdentifier userId,
      LocalDate eventDate,
      String day,
      String statMonth,
      String statYear) {
    if (userId == null) {
      return;
    }

    // Increment login success count (both daily and monthly)
    incrementMetric(tenant, eventDate, "login_success_count");

    // Track DAU - add user to daily active users table and increment DAU count if new
    boolean isNewDailyUser =
        dailyActiveUserRepository.addActiveUserAndReturnIfNew(
            tenant.identifier(), eventDate, userId);

    if (isNewDailyUser) {
      log.debug(
          "New daily active user: tenant={}, date={}, user={}",
          tenant.identifierValue(),
          eventDate,
          userId.value());
      // DAU is daily-only metric, not aggregated to monthly_summary
      statisticsRepository.incrementDailyMetric(tenant.identifier(), statMonth, day, "dau", 1);
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
            tenant.identifier(), statMonth, userId);

    if (isNewMonthlyUser) {
      log.debug(
          "New monthly active user: tenant={}, month={}, user={}",
          tenant.identifierValue(),
          statMonth,
          userId.value());
      // Increment monthly_summary.mau and set cumulative MAU in daily_metrics[day].mau
      statisticsRepository.incrementMauWithDailyCumulative(tenant.identifier(), statMonth, day, 1);
    } else {
      log.debug(
          "User already active this month: tenant={}, month={}, user={}",
          tenant.identifierValue(),
          statMonth,
          userId.value());
    }

    // Track YAU - add user to yearly active users table and increment YAU count if new
    boolean isNewYearlyUser =
        yearlyActiveUserRepository.addActiveUserAndReturnIfNew(
            tenant.identifier(), statYear, userId);

    if (isNewYearlyUser) {
      log.debug(
          "New yearly active user: tenant={}, year={}, user={}",
          tenant.identifierValue(),
          statYear,
          userId.value());
      // Increment yearly_summary.yau
      yearlyStatisticsRepository.incrementYau(tenant.identifier(), statYear, 1);
    } else {
      log.debug(
          "User already active this year: tenant={}, year={}, user={}",
          tenant.identifierValue(),
          statYear,
          userId.value());
    }
  }

  /**
   * Increment a metric in both daily_metrics and monthly_summary
   *
   * <p>Updates both the daily_metrics JSONB field (for daily breakdown) and monthly_summary JSONB
   * field (for monthly totals) within the monthly statistics record. Also updates yearly_summary.
   *
   * @param tenant the tenant
   * @param date statistics date
   * @param metricName metric name to increment
   */
  private void incrementMetric(Tenant tenant, LocalDate date, String metricName) {
    String statMonth = date.format(MONTH_FORMATTER);
    String statYear = date.format(YEAR_FORMATTER);
    String day = date.format(DATE_FORMATTER);

    log.debug(
        "Incrementing metric: tenant={}, month={}, day={}, metric={}",
        tenant.identifierValue(),
        statMonth,
        day,
        metricName);

    // Update daily breakdown
    statisticsRepository.incrementDailyMetric(tenant.identifier(), statMonth, day, metricName, 1);

    // Update monthly total
    statisticsRepository.incrementMonthlySummaryMetric(
        tenant.identifier(), statMonth, metricName, 1);

    // Update yearly total
    yearlyStatisticsRepository.incrementYearlySummaryMetric(
        tenant.identifier(), statYear, metricName, 1);
  }
}
