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
import org.idp.server.platform.security.event.SecurityEventUserIdentifier;

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
 * LocalDate statYear = eventDate.withDayOfYear(1); // First day of year
 * repository.addActiveUserAndReturnIfNew(tenantId, statYear, userId, userName);
 * // Duplicate calls update last_used_at (ON CONFLICT DO UPDATE)
 * }</pre>
 */
public interface YearlyActiveUserCommandRepository {

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
   * @param statYear first day of year (e.g., 2025-01-01)
   * @param userId user identifier to track
   * @param userName user name to store
   * @return true if user was newly added, false if already existed
   */
  boolean addActiveUserAndReturnIfNew(
      TenantIdentifier tenantId,
      LocalDate statYear,
      SecurityEventUserIdentifier userId,
      String userName);
}
