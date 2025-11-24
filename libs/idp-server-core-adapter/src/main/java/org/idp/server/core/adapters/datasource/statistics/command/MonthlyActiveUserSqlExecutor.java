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
import org.idp.server.platform.user.UserIdentifier;

public interface MonthlyActiveUserSqlExecutor {

  void addActiveUser(TenantIdentifier tenantId, LocalDate statMonth, UserIdentifier userId);

  /**
   * Add active user and return whether it was a new user for the month
   *
   * @param tenantId tenant identifier
   * @param statMonth first day of the calendar month
   * @param userId user identifier
   * @return true if user was newly added (not a duplicate), false if already existed
   */
  boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId, LocalDate statMonth, UserIdentifier userId);

  void deleteByMonth(TenantIdentifier tenantId, LocalDate statMonth);

  void deleteOlderThan(LocalDate before);

  void deleteByTenantId(TenantIdentifier tenantId);

  int getMauCount(TenantIdentifier tenantId, LocalDate statMonth);
}
