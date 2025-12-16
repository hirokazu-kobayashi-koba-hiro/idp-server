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

/**
 * Calculator for fiscal year (会計年度) operations.
 *
 * <p>Supports tenant-specific fiscal year configurations where the fiscal year can start in any
 * month (1-12). For example:
 *
 * <ul>
 *   <li>Japan: April (month=4), FY2025 = 2025-04-01 to 2026-03-31
 *   <li>US Federal: October (month=10), FY2025 = 2024-10-01 to 2025-09-30
 *   <li>Calendar year: January (month=1), FY2025 = 2025-01-01 to 2025-12-31
 * </ul>
 */
public class FiscalYearCalculator {

  private FiscalYearCalculator() {}

  /**
   * Calculate fiscal year start date based on the given date and fiscal year start month.
   *
   * <p>Examples for fiscal year starting in April (startMonth=4):
   *
   * <ul>
   *   <li>2025-06-15 → 2025-04-01 (within FY2025)
   *   <li>2025-02-15 → 2024-04-01 (within FY2024)
   *   <li>2025-04-01 → 2025-04-01 (first day of FY2025)
   *   <li>2025-03-31 → 2024-04-01 (last day of FY2024)
   * </ul>
   *
   * @param date the event date
   * @param startMonth fiscal year start month (1-12, where 1=January, 12=December)
   * @return the fiscal year start date (first day of the fiscal year containing the given date)
   * @throws IllegalArgumentException if startMonth is not between 1 and 12
   */
  public static LocalDate calculateFiscalYearStart(LocalDate date, int startMonth) {
    if (startMonth < 1 || startMonth > 12) {
      throw new IllegalArgumentException(
          "startMonth must be between 1 and 12, but was: " + startMonth);
    }

    LocalDate candidateStart = date.withMonth(startMonth).withDayOfMonth(1);
    if (date.isBefore(candidateStart)) {
      return candidateStart.minusYears(1);
    }
    return candidateStart;
  }

  /**
   * Calculate fiscal year end date based on the given date and fiscal year start month.
   *
   * <p>The fiscal year end date is the day before the next fiscal year starts.
   *
   * @param date the event date
   * @param startMonth fiscal year start month (1-12)
   * @return the fiscal year end date (last day of the fiscal year containing the given date)
   */
  public static LocalDate calculateFiscalYearEnd(LocalDate date, int startMonth) {
    LocalDate fiscalYearStart = calculateFiscalYearStart(date, startMonth);
    return fiscalYearStart.plusYears(1).minusDays(1);
  }

  /**
   * Get the fiscal year label for the given date.
   *
   * <p>The fiscal year label is determined by the year of the fiscal year start date. For example,
   * for a fiscal year starting in April:
   *
   * <ul>
   *   <li>2025-06-15 → "FY2025" (fiscal year started 2025-04-01)
   *   <li>2025-02-15 → "FY2024" (fiscal year started 2024-04-01)
   * </ul>
   *
   * @param date the event date
   * @param startMonth fiscal year start month (1-12)
   * @return the fiscal year label (e.g., "FY2025")
   */
  public static String getFiscalYearLabel(LocalDate date, int startMonth) {
    LocalDate fiscalYearStart = calculateFiscalYearStart(date, startMonth);
    return "FY" + fiscalYearStart.getYear();
  }
}
