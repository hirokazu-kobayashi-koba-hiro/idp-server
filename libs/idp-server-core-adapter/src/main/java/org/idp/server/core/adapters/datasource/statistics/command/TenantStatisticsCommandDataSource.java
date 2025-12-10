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

package org.idp.server.core.adapters.datasource.statistics.command;

import java.time.LocalDate;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.repository.TenantStatisticsCommandRepository;

public class TenantStatisticsCommandDataSource implements TenantStatisticsCommandRepository {

  TenantStatisticsSqlExecutor executor;

  public TenantStatisticsCommandDataSource(TenantStatisticsSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void incrementDailyMetric(
      TenantIdentifier tenantId,
      LocalDate statMonth,
      String day,
      String metricName,
      int increment) {
    executor.incrementDailyMetric(tenantId, statMonth, day, metricName, increment);
  }

  @Override
  public void incrementMonthlySummaryMetric(
      TenantIdentifier tenantId, LocalDate statMonth, String metricName, int increment) {
    executor.incrementMonthlySummaryMetric(tenantId, statMonth, metricName, increment);
  }

  @Override
  public void incrementMauWithDailyCumulative(
      TenantIdentifier tenantId, LocalDate statMonth, String day, int increment) {
    executor.incrementMauWithDailyCumulative(tenantId, statMonth, day, increment);
  }
}
