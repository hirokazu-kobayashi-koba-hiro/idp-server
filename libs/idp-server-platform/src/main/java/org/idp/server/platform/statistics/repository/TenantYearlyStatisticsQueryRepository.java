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
import java.util.Optional;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantYearlyStatistics;

/**
 * Query repository for TenantYearlyStatistics
 *
 * <p>Provides read-only operations for yearly tenant statistics retrieval.
 */
public interface TenantYearlyStatisticsQueryRepository {

  /**
   * Find statistics for specific year
   *
   * @param tenant tenant
   * @param statYear target year as LocalDate (first day of year, e.g., 2025-01-01)
   * @return optional statistics (empty if not found)
   */
  Optional<TenantYearlyStatistics> findByYear(Tenant tenant, LocalDate statYear);

  /**
   * Check if statistics exists for year
   *
   * @param tenant tenant
   * @param statYear target year as LocalDate (first day of year)
   * @return true if exists
   */
  boolean exists(Tenant tenant, LocalDate statYear);
}
