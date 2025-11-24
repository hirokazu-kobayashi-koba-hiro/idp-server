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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantStatisticsData;
import org.idp.server.platform.statistics.TenantStatisticsDataIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.statistics.repository.TenantStatisticsDataQueryRepository;

public class TenantStatisticsDataQueryDataSource implements TenantStatisticsDataQueryRepository {

  TenantStatisticsDataSqlExecutor executor;

  public TenantStatisticsDataQueryDataSource(TenantStatisticsDataSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public List<TenantStatisticsData> findByDateRange(
      Tenant tenant, TenantStatisticsQueries queries) {
    List<Map<String, String>> results = executor.selectByDateRange(tenant.identifier(), queries);

    if (results == null || results.isEmpty()) {
      return new ArrayList<>();
    }

    return results.stream().map(ModelConvertor::convert).toList();
  }

  @Override
  public Optional<TenantStatisticsData> findByDate(Tenant tenant, LocalDate date) {
    Map<String, String> result = executor.selectByDate(tenant.identifier(), date);

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConvertor.convert(result));
  }

  @Override
  public TenantStatisticsData get(Tenant tenant, TenantStatisticsDataIdentifier id) {
    Map<String, String> result = executor.selectOne(id);

    if (result == null || result.isEmpty()) {
      throw new NotFoundException("TenantStatisticsData not found: " + id);
    }

    return ModelConvertor.convert(result);
  }

  @Override
  public Optional<TenantStatisticsData> find(Tenant tenant, TenantStatisticsDataIdentifier id) {
    Map<String, String> result = executor.selectOne(id);

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConvertor.convert(result));
  }

  @Override
  public long countByDateRange(Tenant tenant, LocalDate from, LocalDate to) {
    Map<String, String> result = executor.selectCount(tenant.identifier(), from, to);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public Optional<TenantStatisticsData> findLatest(Tenant tenant) {
    Map<String, String> result = executor.selectLatest(tenant.identifier());

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConvertor.convert(result));
  }

  @Override
  public boolean exists(Tenant tenant, LocalDate date) {
    Map<String, String> result = executor.selectExists(tenant.identifier(), date);

    if (result == null || result.isEmpty()) {
      return false;
    }

    long count = Long.parseLong(result.get("count"));
    return count > 0;
  }
}
