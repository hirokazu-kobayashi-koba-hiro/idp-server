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

package org.idp.server.core.adapters.datasource.statistics.query.monthly;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.statistics.repository.TenantStatisticsQueryRepository;

/**
 * DataSource implementation for TenantStatistics queries.
 *
 * <p>Uses TenantStatisticsSqlExecutor to get raw Map data and ModelConvertor to convert to domain
 * model.
 */
public class TenantStatisticsDataQueryDataSource implements TenantStatisticsQueryRepository {

  private final TenantStatisticsSqlExecutor executor;

  public TenantStatisticsDataQueryDataSource(TenantStatisticsSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public List<TenantStatistics> findByMonthRange(Tenant tenant, TenantStatisticsQueries queries) {
    LocalDate fromDate = queries.fromAsLocalDate();
    LocalDate toDate = queries.toAsLocalDate();

    if (fromDate == null || toDate == null) {
      return new ArrayList<>();
    }

    LocalDate toDateEnd = toDate.plusMonths(1);

    List<Map<String, String>> rawEvents =
        executor.selectEvents(tenant.identifier(), fromDate, toDateEnd);

    if (rawEvents == null || rawEvents.isEmpty()) {
      return new ArrayList<>();
    }

    List<TenantStatistics> allStats = ModelConvertor.convertByMonth(tenant.identifier(), rawEvents);

    // Apply pagination
    int offset = queries.offset();
    int limit = queries.limit();
    int endIndex = Math.min(offset + limit, allStats.size());

    if (offset >= allStats.size()) {
      return new ArrayList<>();
    }

    return allStats.subList(offset, endIndex);
  }

  @Override
  public long countByMonthRange(Tenant tenant, LocalDate fromMonth, LocalDate toMonth) {
    Map<String, String> result =
        executor.selectCountDistinctMonths(tenant.identifier(), fromMonth, toMonth);

    if (result == null || result.isEmpty() || result.get("count") == null) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }
}
