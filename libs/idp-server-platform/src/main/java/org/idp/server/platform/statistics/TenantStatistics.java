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

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Tenant monthly statistics
 *
 * <p>Holds monthly statistics with daily breakdown in JSONB format.
 *
 * <p>Structure:
 *
 * <ul>
 *   <li>stat_month - Year and month in YYYY-MM format (e.g., "2025-01")
 *   <li>monthly_summary - Aggregated monthly metrics (e.g., {"mau": 100, "login_success_count":
 *       500})
 *   <li>daily_metrics - Daily breakdown by day number (e.g., {"1": {"dau": 10}, "2": {"dau": 15}})
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * TenantStatistics statistics = TenantStatistics.builder()
 *     .tenantId(tenantId)
 *     .statMonth("2025-01")
 *     .addMonthlySummaryMetric("mau", 100)
 *     .addDailyMetric("1", Map.of("dau", 10, "login_success_count", 50))
 *     .build();
 *
 * Integer mau = statistics.getMonthlySummaryMetric("mau");
 * Map<String, Object> day1Metrics = statistics.getDailyMetrics("1");
 * }</pre>
 */
public class TenantStatistics {

  private final TenantStatisticsIdentifier id;
  private final TenantIdentifier tenantId;
  private final String statMonth;
  private final Map<String, Object> monthlySummary;
  private final Map<String, Map<String, Object>> dailyMetrics;
  private final Instant createdAt;
  private final Instant updatedAt;

  private TenantStatistics(Builder builder) {
    this.id = builder.id;
    this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
    this.statMonth = Objects.requireNonNull(builder.statMonth, "statMonth must not be null");
    this.monthlySummary = Collections.unmodifiableMap(new HashMap<>(builder.monthlySummary));
    this.dailyMetrics = Collections.unmodifiableMap(new HashMap<>(builder.dailyMetrics));
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
  }

  /**
   * Check if monthly summary metric exists
   *
   * @param metricName metric name
   * @return true if exists
   */
  public boolean hasMonthlySummaryMetric(String metricName) {
    return monthlySummary.containsKey(metricName);
  }

  /**
   * Get Integer metric from monthly summary (type-safe)
   *
   * @param name metric name
   * @return Integer value (null if not exists or wrong type)
   */
  public Integer getMonthlySummaryMetric(String name) {
    Object value = monthlySummary.get(name);
    if (value instanceof Integer) {
      return (Integer) value;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return null;
  }

  /**
   * Get daily metrics for a specific day
   *
   * @param day day of month as string (e.g., "1", "15", "31")
   * @return metrics map for the day (null if not exists)
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getDailyMetrics(String day) {
    return dailyMetrics.get(day);
  }

  /**
   * Check if daily metrics exists for a specific day
   *
   * @param day day of month as string
   * @return true if exists
   */
  public boolean hasDailyMetrics(String day) {
    return dailyMetrics.containsKey(day);
  }

  /**
   * Convert to Map (for API response)
   *
   * @return Map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("stat_month", statMonth);
    map.put("monthly_summary", monthlySummary);
    map.put("daily_metrics", dailyMetrics);
    map.put("created_at", createdAt.toString());
    map.put("updated_at", updatedAt.toString());
    return map;
  }

  // Getters

  public TenantStatisticsIdentifier id() {
    return id;
  }

  public TenantIdentifier tenantId() {
    return tenantId;
  }

  public String statMonth() {
    return statMonth;
  }

  public Map<String, Object> monthlySummary() {
    return monthlySummary;
  }

  public Map<String, Map<String, Object>> dailyMetrics() {
    return dailyMetrics;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  // Builder

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private TenantStatisticsIdentifier id;
    private TenantIdentifier tenantId;
    private String statMonth;
    private Map<String, Object> monthlySummary = new HashMap<>();
    private Map<String, Map<String, Object>> dailyMetrics = new HashMap<>();
    private Instant createdAt;
    private Instant updatedAt;

    public Builder id(TenantStatisticsIdentifier id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(TenantIdentifier tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder statMonth(String statMonth) {
      this.statMonth = statMonth;
      return this;
    }

    public Builder monthlySummary(Map<String, Object> monthlySummary) {
      this.monthlySummary = new HashMap<>(monthlySummary);
      return this;
    }

    public Builder addMonthlySummaryMetric(String name, Object value) {
      this.monthlySummary.put(name, value);
      return this;
    }

    public Builder dailyMetrics(Map<String, Map<String, Object>> dailyMetrics) {
      this.dailyMetrics = new HashMap<>(dailyMetrics);
      return this;
    }

    public Builder addDailyMetric(String day, Map<String, Object> metrics) {
      this.dailyMetrics.put(day, new HashMap<>(metrics));
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder updatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public TenantStatistics build() {
      return new TenantStatistics(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TenantStatistics that = (TenantStatistics) o;
    return Objects.equals(id, that.id)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(statMonth, that.statMonth);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId, statMonth);
  }

  @Override
  public String toString() {
    return "TenantStatistics{"
        + "id="
        + id
        + ", tenantId="
        + tenantId
        + ", statMonth="
        + statMonth
        + ", monthlySummarySize="
        + monthlySummary.size()
        + ", dailyMetricsSize="
        + dailyMetrics.size()
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + '}';
  }
}
