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

package org.idp.server.control_plane.management.statistics;

import java.time.LocalDate;
import java.util.*;
import org.idp.server.control_plane.management.statistics.handler.TenantStatisticsManagementService;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.*;
import org.idp.server.platform.statistics.repository.TenantStatisticsQueryRepository;
import org.idp.server.platform.statistics.repository.TenantYearlyStatisticsQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service to find tenant statistics report
 *
 * <p>Combines yearly and monthly statistics into an integrated report for frontend graphing.
 */
public class TenantStatisticsReportFindService
    implements TenantStatisticsManagementService<TenantStatisticsReportQuery> {

  private final TenantStatisticsQueryRepository monthlyRepository;
  private final TenantYearlyStatisticsQueryRepository yearlyRepository;

  public TenantStatisticsReportFindService(
      TenantStatisticsQueryRepository monthlyRepository,
      TenantYearlyStatisticsQueryRepository yearlyRepository) {
    this.monthlyRepository = monthlyRepository;
    this.yearlyRepository = yearlyRepository;
  }

  @Override
  public TenantStatisticsResponse execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      TenantStatisticsReportQuery query,
      RequestAttributes requestAttributes) {

    // Calculate fiscal year start based on tenant configuration
    int fiscalYearStartMonth = tenant.attributes().fiscalYearStartMonth();
    int requestedYear = Integer.parseInt(query.year());

    // Create a date in the middle of the requested year to determine fiscal year
    LocalDate referenceDate = LocalDate.of(requestedYear, fiscalYearStartMonth, 1);
    LocalDate fiscalYearStart =
        FiscalYearCalculator.calculateFiscalYearStart(referenceDate, fiscalYearStartMonth);
    LocalDate fiscalYearEnd =
        FiscalYearCalculator.calculateFiscalYearEnd(referenceDate, fiscalYearStartMonth);

    Optional<TenantYearlyStatistics> yearlyOpt =
        yearlyRepository.findByYear(tenant, fiscalYearStart);

    // Build monthly query range based on fiscal year
    String fromMonth =
        String.format("%04d-%02d", fiscalYearStart.getYear(), fiscalYearStart.getMonthValue());
    String toMonth =
        String.format("%04d-%02d", fiscalYearEnd.getYear(), fiscalYearEnd.getMonthValue());

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("from", fromMonth);
    queryParams.put("to", toMonth);
    queryParams.put("limit", "12");
    queryParams.put("offset", "0");
    TenantStatisticsQueries monthlyQueries = new TenantStatisticsQueries(queryParams);
    List<TenantStatistics> monthlyStatistics =
        monthlyRepository.findByMonthRange(tenant, monthlyQueries);

    TenantStatisticsReport report = buildReport(tenant, query, yearlyOpt, monthlyStatistics);

    return TenantStatisticsResponse.successReport(report);
  }

  private TenantStatisticsReport buildReport(
      Tenant tenant,
      TenantStatisticsReportQuery query,
      Optional<TenantYearlyStatistics> yearlyOpt,
      List<TenantStatistics> monthlyStatistics) {

    Map<String, Object> summary = buildSummary(yearlyOpt);
    List<TenantStatisticsReport.MonthlyData> monthlyDataList =
        buildMonthlyDataList(monthlyStatistics);

    return TenantStatisticsReport.builder()
        .tenantId(tenant.identifier())
        .period(query.year(), query.fromMonth(), query.toMonth())
        .summary(summary)
        .monthly(monthlyDataList)
        .build();
  }

  private Map<String, Object> buildSummary(Optional<TenantYearlyStatistics> yearlyOpt) {
    if (yearlyOpt.isEmpty()) {
      return new HashMap<>();
    }

    TenantYearlyStatistics yearly = yearlyOpt.get();
    Map<String, Object> summary = new HashMap<>(yearly.yearlySummary());
    return summary;
  }

  private List<TenantStatisticsReport.MonthlyData> buildMonthlyDataList(
      List<TenantStatistics> monthlyStatistics) {
    List<TenantStatisticsReport.MonthlyData> result = new ArrayList<>();

    int cumulativeYau = 0;

    List<TenantStatistics> sorted =
        monthlyStatistics.stream()
            .sorted(Comparator.comparing(TenantStatistics::statMonth))
            .toList();

    for (TenantStatistics monthly : sorted) {
      Integer mau = monthly.getMonthlySummaryMetric("mau");
      if (mau != null) {
        cumulativeYau += mau;
      }

      List<TenantStatisticsReport.DailyData> dailyDataList = buildDailyDataList(monthly);

      TenantStatisticsReport.MonthlyData monthlyData =
          TenantStatisticsReport.MonthlyData.builder()
              .month(monthly.statMonth())
              .metrics(monthly.monthlySummary())
              .cumulativeYau(cumulativeYau)
              .daily(dailyDataList)
              .build();

      result.add(monthlyData);
    }

    return result;
  }

  private List<TenantStatisticsReport.DailyData> buildDailyDataList(TenantStatistics monthly) {
    List<TenantStatisticsReport.DailyData> result = new ArrayList<>();

    Map<String, Map<String, Object>> dailyMetrics = monthly.dailyMetrics();
    if (dailyMetrics == null || dailyMetrics.isEmpty()) {
      return result;
    }

    List<String> sortedDays = dailyMetrics.keySet().stream().sorted().toList();

    for (String day : sortedDays) {
      Map<String, Object> metrics = dailyMetrics.get(day);

      TenantStatisticsReport.DailyData dailyData =
          TenantStatisticsReport.DailyData.builder().day(day).metrics(metrics).build();

      result.add(dailyData);
    }

    return result;
  }
}
