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
import org.idp.server.platform.statistics.TenantStatisticsData;
import org.idp.server.platform.statistics.TenantStatisticsDataIdentifier;

public interface TenantStatisticsDataSqlExecutor {

  void upsert(TenantStatisticsData data);

  void insert(TenantStatisticsData data);

  void update(TenantStatisticsData data);

  void delete(TenantStatisticsDataIdentifier id);

  void deleteByDate(TenantIdentifier tenantId, LocalDate date);

  void deleteOlderThan(LocalDate before);

  void deleteByTenantId(TenantIdentifier tenantId);

  void incrementMetric(TenantIdentifier tenantId, LocalDate date, String metricName, int increment);
}
