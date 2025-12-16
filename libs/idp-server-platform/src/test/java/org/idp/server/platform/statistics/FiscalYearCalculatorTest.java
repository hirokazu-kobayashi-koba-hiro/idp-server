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

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FiscalYearCalculatorTest {

  @Nested
  @DisplayName("calculateFiscalYearStart")
  class CalculateFiscalYearStartTest {

    @Nested
    @DisplayName("April start (Japanese companies)")
    class AprilStartTest {

      @Test
      @DisplayName("June 15 returns current fiscal year April 1")
      void midYear_returnsCurrentFiscalYear() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 4);
        assertEquals(LocalDate.of(2025, 4, 1), result);
      }

      @Test
      @DisplayName("February 15 returns previous fiscal year April 1")
      void beforeFiscalYearStart_returnsPreviousFiscalYear() {
        LocalDate date = LocalDate.of(2025, 2, 15);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 4);
        assertEquals(LocalDate.of(2024, 4, 1), result);
      }

      @Test
      @DisplayName("April 1 returns current fiscal year April 1 (boundary)")
      void exactlyOnFiscalYearStart_returnsCurrentFiscalYear() {
        LocalDate date = LocalDate.of(2025, 4, 1);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 4);
        assertEquals(LocalDate.of(2025, 4, 1), result);
      }

      @Test
      @DisplayName("March 31 returns previous fiscal year April 1 (boundary)")
      void dayBeforeFiscalYearStart_returnsPreviousFiscalYear() {
        LocalDate date = LocalDate.of(2025, 3, 31);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 4);
        assertEquals(LocalDate.of(2024, 4, 1), result);
      }

      @Test
      @DisplayName("December 31 returns current fiscal year April 1")
      void endOfCalendarYear_returnsCurrentFiscalYear() {
        LocalDate date = LocalDate.of(2025, 12, 31);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 4);
        assertEquals(LocalDate.of(2025, 4, 1), result);
      }

      @Test
      @DisplayName("January 1 returns previous fiscal year April 1")
      void startOfCalendarYear_returnsPreviousFiscalYear() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 4);
        assertEquals(LocalDate.of(2024, 4, 1), result);
      }
    }

    @Nested
    @DisplayName("October start (US Federal Government)")
    class OctoberStartTest {

      @Test
      @DisplayName("November 15 returns current fiscal year October 1")
      void afterFiscalYearStart_returnsCurrentFiscalYear() {
        LocalDate date = LocalDate.of(2025, 11, 15);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 10);
        assertEquals(LocalDate.of(2025, 10, 1), result);
      }

      @Test
      @DisplayName("September 30 returns previous fiscal year October 1 (boundary)")
      void dayBeforeFiscalYearStart_returnsPreviousFiscalYear() {
        LocalDate date = LocalDate.of(2025, 9, 30);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 10);
        assertEquals(LocalDate.of(2024, 10, 1), result);
      }

      @Test
      @DisplayName("October 1 returns current fiscal year October 1 (boundary)")
      void exactlyOnFiscalYearStart_returnsCurrentFiscalYear() {
        LocalDate date = LocalDate.of(2025, 10, 1);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 10);
        assertEquals(LocalDate.of(2025, 10, 1), result);
      }

      @Test
      @DisplayName("March 15 returns previous fiscal year October 1")
      void midCalendarYear_returnsPreviousFiscalYear() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 10);
        assertEquals(LocalDate.of(2024, 10, 1), result);
      }
    }

    @Nested
    @DisplayName("January start (calendar year)")
    class JanuaryStartTest {

      @Test
      @DisplayName("June 15 returns current year January 1")
      void midYear_returnsCurrentYear() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 1);
        assertEquals(LocalDate.of(2025, 1, 1), result);
      }

      @Test
      @DisplayName("January 1 returns current year January 1 (boundary)")
      void firstDayOfYear_returnsCurrentYear() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 1);
        assertEquals(LocalDate.of(2025, 1, 1), result);
      }

      @Test
      @DisplayName("December 31 returns current year January 1")
      void lastDayOfYear_returnsCurrentYear() {
        LocalDate date = LocalDate.of(2025, 12, 31);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 1);
        assertEquals(LocalDate.of(2025, 1, 1), result);
      }
    }

    @Nested
    @DisplayName("Edge cases and errors")
    class EdgeCasesTest {

      @Test
      @DisplayName("startMonth=0 throws IllegalArgumentException")
      void invalidStartMonthZero_throwsException() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        assertThrows(
            IllegalArgumentException.class,
            () -> FiscalYearCalculator.calculateFiscalYearStart(date, 0));
      }

      @Test
      @DisplayName("startMonth=13 throws IllegalArgumentException")
      void invalidStartMonthThirteen_throwsException() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        assertThrows(
            IllegalArgumentException.class,
            () -> FiscalYearCalculator.calculateFiscalYearStart(date, 13));
      }

      @Test
      @DisplayName("startMonth=-1 throws IllegalArgumentException")
      void invalidStartMonthNegative_throwsException() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        assertThrows(
            IllegalArgumentException.class,
            () -> FiscalYearCalculator.calculateFiscalYearStart(date, -1));
      }

      @Test
      @DisplayName("Leap year February 29 with January start")
      void leapYearDate_handledCorrectly() {
        LocalDate date = LocalDate.of(2024, 2, 29);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 1);
        assertEquals(LocalDate.of(2024, 1, 1), result);
      }

      @Test
      @DisplayName("Leap year February 29 with April start")
      void leapYearDate_aprilStart() {
        LocalDate date = LocalDate.of(2024, 2, 29);
        LocalDate result = FiscalYearCalculator.calculateFiscalYearStart(date, 4);
        assertEquals(LocalDate.of(2023, 4, 1), result);
      }
    }
  }

  @Nested
  @DisplayName("calculateFiscalYearEnd")
  class CalculateFiscalYearEndTest {

    @Test
    @DisplayName("April start: 2025-06-15 returns 2026-03-31")
    void aprilStart_midYear() {
      LocalDate date = LocalDate.of(2025, 6, 15);
      LocalDate result = FiscalYearCalculator.calculateFiscalYearEnd(date, 4);
      assertEquals(LocalDate.of(2026, 3, 31), result);
    }

    @Test
    @DisplayName("April start: 2025-02-15 returns 2025-03-31")
    void aprilStart_beforeFiscalYearStart() {
      LocalDate date = LocalDate.of(2025, 2, 15);
      LocalDate result = FiscalYearCalculator.calculateFiscalYearEnd(date, 4);
      assertEquals(LocalDate.of(2025, 3, 31), result);
    }

    @Test
    @DisplayName("January start: 2025-06-15 returns 2025-12-31")
    void januaryStart_midYear() {
      LocalDate date = LocalDate.of(2025, 6, 15);
      LocalDate result = FiscalYearCalculator.calculateFiscalYearEnd(date, 1);
      assertEquals(LocalDate.of(2025, 12, 31), result);
    }

    @Test
    @DisplayName("October start: 2025-11-15 returns 2026-09-30")
    void octoberStart_afterFiscalYearStart() {
      LocalDate date = LocalDate.of(2025, 11, 15);
      LocalDate result = FiscalYearCalculator.calculateFiscalYearEnd(date, 10);
      assertEquals(LocalDate.of(2026, 9, 30), result);
    }
  }

  @Nested
  @DisplayName("getFiscalYearLabel")
  class GetFiscalYearLabelTest {

    @Test
    @DisplayName("April start: 2025-06-15 returns FY2025")
    void aprilStart_midYear() {
      LocalDate date = LocalDate.of(2025, 6, 15);
      String result = FiscalYearCalculator.getFiscalYearLabel(date, 4);
      assertEquals("FY2025", result);
    }

    @Test
    @DisplayName("April start: 2025-02-15 returns FY2024")
    void aprilStart_beforeFiscalYearStart() {
      LocalDate date = LocalDate.of(2025, 2, 15);
      String result = FiscalYearCalculator.getFiscalYearLabel(date, 4);
      assertEquals("FY2024", result);
    }

    @Test
    @DisplayName("January start: 2025-06-15 returns FY2025")
    void januaryStart_midYear() {
      LocalDate date = LocalDate.of(2025, 6, 15);
      String result = FiscalYearCalculator.getFiscalYearLabel(date, 1);
      assertEquals("FY2025", result);
    }

    @Test
    @DisplayName("October start: 2025-11-15 returns FY2025")
    void octoberStart_afterFiscalYearStart() {
      LocalDate date = LocalDate.of(2025, 11, 15);
      String result = FiscalYearCalculator.getFiscalYearLabel(date, 10);
      assertEquals("FY2025", result);
    }

    @Test
    @DisplayName("October start: 2025-09-15 returns FY2024")
    void octoberStart_beforeFiscalYearStart() {
      LocalDate date = LocalDate.of(2025, 9, 15);
      String result = FiscalYearCalculator.getFiscalYearLabel(date, 10);
      assertEquals("FY2024", result);
    }
  }
}
