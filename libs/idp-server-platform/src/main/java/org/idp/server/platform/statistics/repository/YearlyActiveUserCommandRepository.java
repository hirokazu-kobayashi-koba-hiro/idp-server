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

import java.time.LocalDateTime;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.user.UserIdentifier;

/**
 * Command repository for yearly active users (YAU)
 *
 * <p>Provides write operations for tracking unique yearly active users.
 *
 * <p>Uses separate table (statistics_yearly_users) to enable real-time YAU calculation.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Track user login for YAU
 * String statYear = "2025"; // YYYY format
 * repository.addActiveUser(tenantId, statYear, userId);
 * // Duplicate calls update last_used_at (ON CONFLICT DO UPDATE)
 * }</pre>
 */
public interface YearlyActiveUserCommandRepository {

  /**
   * Add a user to yearly active users or update last_used_at if exists
   *
   * <p>If the same (tenant_id, stat_year, user_id) already exists, updates last_used_at to current
   * timestamp (ON CONFLICT DO UPDATE).
   *
   * <p>This ensures each user is counted only once per calendar year while tracking last activity.
   *
   * @param tenantId tenant identifier
   * @param statYear year in YYYY format (e.g., 2025)
   * @param userId user identifier to track
   */
  void addActiveUser(TenantIdentifier tenantId, String statYear, UserIdentifier userId);

  /**
   * Add a user to yearly active users and return whether it was new
   *
   * <p>Returns true if the user was newly added (first activity of the year), false if the user was
   * already active this year.
   *
   * <p>Updates last_used_at regardless of whether user is new or existing.
   *
   * <p>Useful for incrementing YAU count only when a new unique user appears.
   *
   * @param tenantId tenant identifier
   * @param statYear year in YYYY format (e.g., 2025)
   * @param userId user identifier to track
   * @return true if user was newly added, false if already existed
   */
  boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId, String statYear, UserIdentifier userId);

  /**
   * Get the last used timestamp for a user in a specific year
   *
   * @param tenantId tenant identifier
   * @param statYear year in YYYY format
   * @param userId user identifier
   * @return last used timestamp, or null if not found
   */
  LocalDateTime getLastUsedAt(TenantIdentifier tenantId, String statYear, UserIdentifier userId);

  /**
   * Delete active users by year
   *
   * @param tenantId tenant identifier
   * @param statYear year in YYYY format
   */
  void deleteByYear(TenantIdentifier tenantId, String statYear);

  /**
   * Delete old active user data (data retention)
   *
   * <p>Example: Delete data older than 5 years
   *
   * <pre>{@code
   * repository.deleteOlderThan("2020"); // Delete all data before 2020
   * }</pre>
   *
   * @param beforeYear delete data older than this year (exclusive, YYYY format)
   */
  void deleteOlderThan(String beforeYear);

  /**
   * Delete all active user data for a tenant
   *
   * @param tenantId tenant identifier
   */
  void deleteByTenantId(TenantIdentifier tenantId);
}
