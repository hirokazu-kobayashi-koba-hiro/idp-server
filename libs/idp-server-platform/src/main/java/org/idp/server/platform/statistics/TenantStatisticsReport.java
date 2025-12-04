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

import java.util.*;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Integrated statistics report for a tenant
 *
 * <p>Combines yearly and monthly statistics for frontend graphing. Designed for time-series
 * visualization using libraries like Chart.js or Recharts.
 *
 * <p>Response structure (案1):
 *
 * <pre>{@code
 * {
 *   "period": { "year": "2025", "from_month": "2025-01", "to_month": "2025-12" },
 *   "summary": { "yau": 1500, "total_login_success": 50000 },
 *   "monthly": [
 *     {
 *       "month": "2025-01",
 *       "mau": 120,
 *       "cumulative_yau": 120,
 *       "daily": [
 *         { "day": "2025-01-01", "dau": 10, "login_success_count": 150 },
 *         { "day": "2025-01-02", "dau": 12, "login_success_count": 180 }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 */
public class TenantStatisticsReport {

  private final TenantIdentifier tenantId;
  private final Period period;
  private final Map<String, Object> summary;
  private final List<MonthlyData> monthly;

  private TenantStatisticsReport(Builder builder) {
    this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
    this.period = Objects.requireNonNull(builder.period, "period must not be null");
    this.summary = Collections.unmodifiableMap(new HashMap<>(builder.summary));
    this.monthly = Collections.unmodifiableList(new ArrayList<>(builder.monthly));
  }

  public TenantIdentifier tenantId() {
    return tenantId;
  }

  public Period period() {
    return period;
  }

  public Map<String, Object> summary() {
    return summary;
  }

  public List<MonthlyData> monthly() {
    return monthly;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("period", period.toMap());
    map.put("summary", summary);
    map.put("monthly", monthly.stream().map(MonthlyData::toMap).toList());
    return map;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Period information */
  public static class Period {
    private final String year;
    private final String fromMonth;
    private final String toMonth;

    public Period(String year, String fromMonth, String toMonth) {
      this.year = year;
      this.fromMonth = fromMonth;
      this.toMonth = toMonth;
    }

    public String year() {
      return year;
    }

    public String fromMonth() {
      return fromMonth;
    }

    public String toMonth() {
      return toMonth;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("year", year);
      map.put("from_month", fromMonth);
      map.put("to_month", toMonth);
      return map;
    }
  }

  /** Monthly data with daily breakdown */
  public static class MonthlyData {
    private final String month;
    private final Map<String, Object> metrics;
    private final int cumulativeYau;
    private final List<DailyData> daily;

    private MonthlyData(MonthlyDataBuilder builder) {
      this.month = Objects.requireNonNull(builder.month, "month must not be null");
      this.metrics = Collections.unmodifiableMap(new HashMap<>(builder.metrics));
      this.cumulativeYau = builder.cumulativeYau;
      this.daily = Collections.unmodifiableList(new ArrayList<>(builder.daily));
    }

    public String month() {
      return month;
    }

    public Map<String, Object> metrics() {
      return metrics;
    }

    public int cumulativeYau() {
      return cumulativeYau;
    }

    public List<DailyData> daily() {
      return daily;
    }

    public Integer mau() {
      Object value = metrics.get("mau");
      if (value instanceof Integer) {
        return (Integer) value;
      }
      if (value instanceof Number) {
        return ((Number) value).intValue();
      }
      return null;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("month", month);
      map.putAll(metrics);
      map.put("cumulative_yau", cumulativeYau);
      map.put("daily", daily.stream().map(DailyData::toMap).toList());
      return map;
    }

    public static MonthlyDataBuilder builder() {
      return new MonthlyDataBuilder();
    }

    public static class MonthlyDataBuilder {
      private String month;
      private Map<String, Object> metrics = new HashMap<>();
      private int cumulativeYau;
      private List<DailyData> daily = new ArrayList<>();

      public MonthlyDataBuilder month(String month) {
        this.month = month;
        return this;
      }

      public MonthlyDataBuilder metrics(Map<String, Object> metrics) {
        this.metrics = new HashMap<>(metrics);
        return this;
      }

      public MonthlyDataBuilder addMetric(String name, Object value) {
        this.metrics.put(name, value);
        return this;
      }

      public MonthlyDataBuilder cumulativeYau(int cumulativeYau) {
        this.cumulativeYau = cumulativeYau;
        return this;
      }

      public MonthlyDataBuilder daily(List<DailyData> daily) {
        this.daily = new ArrayList<>(daily);
        return this;
      }

      public MonthlyDataBuilder addDaily(DailyData dailyData) {
        this.daily.add(dailyData);
        return this;
      }

      public MonthlyData build() {
        return new MonthlyData(this);
      }
    }
  }

  /** Daily data */
  public static class DailyData {
    private final String day;
    private final Map<String, Object> metrics;

    private DailyData(DailyDataBuilder builder) {
      this.day = Objects.requireNonNull(builder.day, "day must not be null");
      this.metrics = Collections.unmodifiableMap(new HashMap<>(builder.metrics));
    }

    public String day() {
      return day;
    }

    public Map<String, Object> metrics() {
      return metrics;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("day", day);
      map.putAll(metrics);
      return map;
    }

    public static DailyDataBuilder builder() {
      return new DailyDataBuilder();
    }

    public static class DailyDataBuilder {
      private String day;
      private Map<String, Object> metrics = new HashMap<>();

      public DailyDataBuilder day(String day) {
        this.day = day;
        return this;
      }

      public DailyDataBuilder metrics(Map<String, Object> metrics) {
        this.metrics = new HashMap<>(metrics);
        return this;
      }

      public DailyDataBuilder addMetric(String name, Object value) {
        this.metrics.put(name, value);
        return this;
      }

      public DailyData build() {
        return new DailyData(this);
      }
    }
  }

  public static class Builder {
    private TenantIdentifier tenantId;
    private Period period;
    private Map<String, Object> summary = new HashMap<>();
    private List<MonthlyData> monthly = new ArrayList<>();

    public Builder tenantId(TenantIdentifier tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder period(Period period) {
      this.period = period;
      return this;
    }

    public Builder period(String year, String fromMonth, String toMonth) {
      this.period = new Period(year, fromMonth, toMonth);
      return this;
    }

    public Builder summary(Map<String, Object> summary) {
      this.summary = new HashMap<>(summary);
      return this;
    }

    public Builder addSummaryMetric(String name, Object value) {
      this.summary.put(name, value);
      return this;
    }

    public Builder monthly(List<MonthlyData> monthly) {
      this.monthly = new ArrayList<>(monthly);
      return this;
    }

    public Builder addMonthly(MonthlyData monthlyData) {
      this.monthly.add(monthlyData);
      return this;
    }

    public TenantStatisticsReport build() {
      return new TenantStatisticsReport(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TenantStatisticsReport that = (TenantStatisticsReport) o;
    return Objects.equals(tenantId, that.tenantId) && Objects.equals(period.year, that.period.year);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, period.year);
  }

  @Override
  public String toString() {
    return "TenantStatisticsReport{"
        + "tenantId="
        + tenantId
        + ", period="
        + period.year
        + ", summarySize="
        + summary.size()
        + ", monthlySize="
        + monthly.size()
        + '}';
  }
}
