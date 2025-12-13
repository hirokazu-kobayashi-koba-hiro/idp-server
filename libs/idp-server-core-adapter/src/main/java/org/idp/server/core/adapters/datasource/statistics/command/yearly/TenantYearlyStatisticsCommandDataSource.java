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

package org.idp.server.core.adapters.datasource.statistics.command.yearly;

import java.time.LocalDate;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.repository.TenantYearlyStatisticsCommandRepository;

public class TenantYearlyStatisticsCommandDataSource
    implements TenantYearlyStatisticsCommandRepository {

  TenantYearlyStatisticsSqlExecutor executor;

  public TenantYearlyStatisticsCommandDataSource(TenantYearlyStatisticsSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void incrementYearlySummaryMetric(
      TenantIdentifier tenantId, LocalDate statYear, String metricName, int increment) {
    executor.incrementYearlySummaryMetric(tenantId, statYear, metricName, increment);
  }

  @Override
  public void incrementYau(TenantIdentifier tenantId, LocalDate statYear, int increment) {
    executor.incrementYau(tenantId, statYear, increment);
  }
}
