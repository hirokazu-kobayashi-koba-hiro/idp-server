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

package org.idp.server.platform.statistics.repository;

import java.time.LocalDate;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.user.UserIdentifier;

/**
 * Command repository for monthly active users (MAU)
 *
 * <p>Provides write operations for tracking unique monthly active users.
 *
 * <p>Uses separate table (monthly_active_users) to enable real-time MAU calculation.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Track user login for MAU
 * LocalDate statMonth = LocalDate.now().withDayOfMonth(1); // First day of month
 * repository.addActiveUser(tenantId, statMonth, userId);
 * // Duplicate calls are automatically ignored (PRIMARY KEY constraint)
 * }</pre>
 */
public interface MonthlyActiveUserCommandRepository {

  /**
   * Add a user to monthly active users
   *
   * <p>If the same (tenant_id, stat_month, user_id) already exists, the operation is silently
   * ignored (ON CONFLICT DO NOTHING).
   *
   * <p>This ensures each user is counted only once per calendar month.
   *
   * @param tenantId tenant identifier
   * @param statMonth first day of the calendar month (e.g., 2025-01-01)
   * @param userId user identifier to track
   */
  void addActiveUser(TenantIdentifier tenantId, LocalDate statMonth, UserIdentifier userId);

  /**
   * Add a user to monthly active users and return whether it was new
   *
   * <p>Returns true if the user was newly added (first activity of the month), false if the user
   * was already active this month.
   *
   * <p>Useful for incrementing MAU count only when a new unique user appears.
   *
   * @param tenantId tenant identifier
   * @param statMonth first day of the calendar month (e.g., 2025-01-01)
   * @param userId user identifier to track
   * @return true if user was newly added, false if already existed
   */
  boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId, LocalDate statMonth, UserIdentifier userId);

  /**
   * Delete active users by month
   *
   * @param tenantId tenant identifier
   * @param statMonth first day of the calendar month
   */
  void deleteByMonth(TenantIdentifier tenantId, LocalDate statMonth);

  /**
   * Delete old active user data (data retention)
   *
   * <p>Example: Delete data older than 365 days
   *
   * <pre>{@code
   * repository.deleteOlderThan(LocalDate.now().minusDays(365));
   * }</pre>
   *
   * @param before delete data older than this date (exclusive)
   */
  void deleteOlderThan(LocalDate before);

  /**
   * Delete all active user data for a tenant
   *
   * @param tenantId tenant identifier
   */
  void deleteByTenantId(TenantIdentifier tenantId);
}
