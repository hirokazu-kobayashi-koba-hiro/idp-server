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
import org.idp.server.platform.statistics.TenantStatistics;
import org.idp.server.platform.statistics.TenantStatisticsIdentifier;
import org.idp.server.platform.statistics.repository.TenantStatisticsCommandRepository;

public class TenantStatisticsCommandDataSource implements TenantStatisticsCommandRepository {

  TenantStatisticsSqlExecutor executor;

  public TenantStatisticsCommandDataSource(TenantStatisticsSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void save(TenantStatistics data) {
    executor.upsert(data);
  }

  @Override
  public void register(TenantStatistics data) {
    executor.insert(data);
  }

  @Override
  public void update(TenantStatistics data) {
    executor.update(data);
  }

  @Override
  public void delete(TenantStatisticsIdentifier id) {
    executor.delete(id);
  }

  @Override
  public void deleteByDate(TenantIdentifier tenantId, LocalDate date) {
    executor.deleteByDate(tenantId, date);
  }

  @Override
  public void deleteOlderThan(LocalDate before) {
    executor.deleteOlderThan(before);
  }

  @Override
  public void deleteByTenantId(TenantIdentifier tenantId) {
    executor.deleteByTenantId(tenantId);
  }

  @Override
  public void incrementMetric(
      TenantIdentifier tenantId, LocalDate date, String metricName, int increment) {
    executor.incrementMetric(tenantId, date, metricName, increment);
  }
}
