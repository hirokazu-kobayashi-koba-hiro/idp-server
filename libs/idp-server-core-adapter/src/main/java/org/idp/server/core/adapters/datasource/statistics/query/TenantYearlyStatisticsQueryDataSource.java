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

package org.idp.server.core.adapters.datasource.statistics.query;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantYearlyStatistics;
import org.idp.server.platform.statistics.repository.TenantYearlyStatisticsQueryRepository;

public class TenantYearlyStatisticsQueryDataSource
    implements TenantYearlyStatisticsQueryRepository {

  TenantYearlyStatisticsSqlExecutor executor;

  public TenantYearlyStatisticsQueryDataSource(TenantYearlyStatisticsSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public Optional<TenantYearlyStatistics> findByYear(Tenant tenant, LocalDate statYear) {
    Map<String, String> result = executor.selectByYear(tenant.identifier(), statYear);

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConvertor.convertYearly(result));
  }

  @Override
  public boolean exists(Tenant tenant, LocalDate statYear) {
    Map<String, String> result = executor.selectExists(tenant.identifier(), statYear);

    if (result == null || result.isEmpty()) {
      return false;
    }

    long count = Long.parseLong(result.get("count"));
    return count > 0;
  }
}
