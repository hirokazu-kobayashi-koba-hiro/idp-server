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
 * Command repository for daily active users
 *
 * <p>Provides write operations for tracking unique daily active users.
 *
 * <p>Uses separate table (daily_active_users) to prevent JSONB bloat and ensure scalability.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Track user login for DAU
 * repository.addActiveUser(tenantId, LocalDate.now(), userId);
 * // Duplicate calls are automatically ignored (PRIMARY KEY constraint)
 * }</pre>
 */
public interface DailyActiveUserCommandRepository {

  /**
   * Add a user to daily active users
   *
   * <p>If the same (tenant_id, stat_date, user_id) already exists, the operation is silently
   * ignored (ON CONFLICT DO NOTHING).
   *
   * <p>This ensures each user is counted only once per day.
   *
   * @param tenantId tenant identifier
   * @param date statistics date
   * @param userId user identifier to track
   */
  void addActiveUser(TenantIdentifier tenantId, LocalDate date, UserIdentifier userId);

  /**
   * Add a user to daily active users and return whether it was new
   *
   * <p>Returns true if the user was newly added (first activity of the day), false if the user was
   * already active today.
   *
   * <p>Useful for incrementing DAU count only when a new unique user appears.
   *
   * @param tenantId tenant identifier
   * @param date statistics date
   * @param userId user identifier to track
   * @return true if user was newly added, false if already existed
   */
  boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId, LocalDate date, UserIdentifier userId);

  /**
   * Delete active users by date
   *
   * @param tenantId tenant identifier
   * @param date target date
   */
  void deleteByDate(TenantIdentifier tenantId, LocalDate date);

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
