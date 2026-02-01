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

package org.idp.server.core.adapters.datasource.statistics.command.events;

import java.time.LocalDate;
import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;
import org.idp.server.platform.statistics.repository.StatisticsEventsCommandRepository;

/**
 * Data source implementation for statistics events.
 *
 * <p>Delegates to database-specific SQL executor for upsert operations.
 */
public class StatisticsEventsCommandDataSource implements StatisticsEventsCommandRepository {

  private final StatisticsEventsSqlExecutor executor;

  public StatisticsEventsCommandDataSource(StatisticsEventsSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void increment(TenantIdentifier tenantId, LocalDate statDate, String eventType) {
    executor.increment(tenantId, statDate, eventType);
  }

  @Override
  public void batchUpsert(List<StatisticsEventRecord> records) {
    executor.batchUpsert(records);
  }
}
