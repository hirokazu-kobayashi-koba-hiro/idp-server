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
import org.idp.server.platform.statistics.repository.MonthlyActiveUserCommandRepository;
import org.idp.server.platform.user.UserIdentifier;

public class MonthlyActiveUserCommandDataSource implements MonthlyActiveUserCommandRepository {

  MonthlyActiveUserSqlExecutor executor;

  public MonthlyActiveUserCommandDataSource(MonthlyActiveUserSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void addActiveUser(TenantIdentifier tenantId, LocalDate statMonth, UserIdentifier userId) {
    executor.addActiveUser(tenantId, statMonth, userId);
  }

  @Override
  public boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId, LocalDate statMonth, UserIdentifier userId) {
    return executor.addActiveUserAndReturnIfNew(tenantId, statMonth, userId);
  }

  @Override
  public void deleteByMonth(TenantIdentifier tenantId, LocalDate statMonth) {
    executor.deleteByMonth(tenantId, statMonth);
  }

  @Override
  public void deleteOlderThan(LocalDate before) {
    executor.deleteOlderThan(before);
  }

  @Override
  public void deleteByTenantId(TenantIdentifier tenantId) {
    executor.deleteByTenantId(tenantId);
  }
}
