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

/**
 * Query repository for daily active users
 *
 * <p>Provides read operations for retrieving DAU counts.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Get DAU for today
 * int dau = repository.getDauCount(tenantId, LocalDate.now());
 * }</pre>
 */
public interface DailyActiveUserQueryRepository {

  /**
   * Get DAU count for a specific date
   *
   * <p>Counts unique users who were active on the specified date.
   *
   * @param tenantId tenant identifier
   * @param date statistics date
   * @return number of unique active users (0 if no data)
   */
  int getDauCount(TenantIdentifier tenantId, LocalDate date);
}
