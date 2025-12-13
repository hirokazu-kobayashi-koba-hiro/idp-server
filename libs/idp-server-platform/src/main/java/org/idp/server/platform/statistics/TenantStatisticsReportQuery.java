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

package org.idp.server.platform.statistics;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Query parameters for tenant statistics report
 *
 * <p>Used to request yearly statistics report with monthly breakdown.
 */
public class TenantStatisticsReportQuery {

  private final String year;

  public TenantStatisticsReportQuery(String year) {
    this.year = Objects.requireNonNull(year, "year must not be null");
  }

  public String year() {
    return year;
  }

  public LocalDate yearAsLocalDate() {
    int yearInt = Integer.parseInt(year);
    return LocalDate.of(yearInt, 1, 1);
  }

  public String fromMonth() {
    return year + "-01";
  }

  public String toMonth() {
    return year + "-12";
  }

  public boolean exists() {
    return year != null && !year.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TenantStatisticsReportQuery that = (TenantStatisticsReportQuery) o;
    return Objects.equals(year, that.year);
  }

  @Override
  public int hashCode() {
    return Objects.hash(year);
  }

  @Override
  public String toString() {
    return "TenantStatisticsReportQuery{" + "year='" + year + '\'' + '}';
  }
}
