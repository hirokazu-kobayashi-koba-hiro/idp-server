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
import java.util.Optional;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.statistics.repository.TenantStatisticsQueryRepository;

public class TenantStatisticsDataQueryDataSource implements TenantStatisticsQueryRepository {

  TenantStatisticsSqlExecutor executor;

  public TenantStatisticsDataQueryDataSource(TenantStatisticsSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public List<TenantStatistics> findByMonthRange(Tenant tenant, TenantStatisticsQueries queries) {
    List<Map<String, String>> results = executor.selectByMonthRange(tenant.identifier(), queries);

    if (results == null || results.isEmpty()) {
      return new ArrayList<>();
    }

    return results.stream().map(ModelConvertor::convert).toList();
  }

  @Override
  public Optional<TenantStatistics> findByMonth(Tenant tenant, LocalDate statMonth) {
    Map<String, String> result = executor.selectByMonth(tenant.identifier(), statMonth);

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConvertor.convert(result));
  }

  @Override
  public TenantStatistics get(Tenant tenant, TenantStatisticsIdentifier id) {
    Map<String, String> result = executor.selectOne(id);

    if (result == null || result.isEmpty()) {
      throw new NotFoundException("TenantStatistics not found: " + id);
    }

    return ModelConvertor.convert(result);
  }

  @Override
  public Optional<TenantStatistics> find(Tenant tenant, TenantStatisticsIdentifier id) {
    Map<String, String> result = executor.selectOne(id);

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConvertor.convert(result));
  }

  @Override
  public long countByMonthRange(Tenant tenant, LocalDate fromMonth, LocalDate toMonth) {
    Map<String, String> result = executor.selectCount(tenant.identifier(), fromMonth, toMonth);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public Optional<TenantStatistics> findLatest(Tenant tenant) {
    Map<String, String> result = executor.selectLatest(tenant.identifier());

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConvertor.convert(result));
  }

  @Override
  public boolean exists(Tenant tenant, LocalDate statMonth) {
    Map<String, String> result = executor.selectExists(tenant.identifier(), statMonth);

    if (result == null || result.isEmpty()) {
      return false;
    }

    long count = Long.parseLong(result.get("count"));
    return count > 0;
  }
}
