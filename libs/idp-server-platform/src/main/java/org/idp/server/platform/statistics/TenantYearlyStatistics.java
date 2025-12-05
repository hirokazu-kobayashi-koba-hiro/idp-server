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
 * Tenant yearly statistics
 *
 * <p>Holds yearly statistics summary.
 *
 * <p>Structure:
 *
 * <ul>
 *   <li>stat_year - Year in YYYY format (e.g., "2025")
 *   <li>yearly_summary - Aggregated yearly metrics (e.g., {"yau": 1500, "login_success_count":
 *       50000})
 * </ul>
 *
 * <p>Note: Monthly breakdown is available via statistics_monthly table, not duplicated here.
 */
public class TenantYearlyStatistics {

  private final TenantYearlyStatisticsIdentifier id;
  private final TenantIdentifier tenantId;
  private final String statYear;
  private final Map<String, Object> yearlySummary;
  private final Instant createdAt;
  private final Instant updatedAt;

  private TenantYearlyStatistics(Builder builder) {
    this.id = builder.id;
    this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
    this.statYear = Objects.requireNonNull(builder.statYear, "statYear must not be null");
    this.yearlySummary = Collections.unmodifiableMap(new HashMap<>(builder.yearlySummary));
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
  }

  /**
   * Get YAU (Yearly Active Users) count
   *
   * @return YAU count (0 if not exists)
   */
  public int yau() {
    Integer yau = getYearlySummaryMetric("yau");
    return yau != null ? yau : 0;
  }

  /**
   * Check if yearly summary metric exists
   *
   * @param metricName metric name
   * @return true if exists
   */
  public boolean hasYearlySummaryMetric(String metricName) {
    return yearlySummary.containsKey(metricName);
  }

  /**
   * Get Integer metric from yearly summary (type-safe)
   *
   * @param name metric name
   * @return Integer value (null if not exists or wrong type)
   */
  public Integer getYearlySummaryMetric(String name) {
    Object value = yearlySummary.get(name);
    if (value instanceof Integer) {
      return (Integer) value;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return null;
  }

  /**
   * Convert to Map (for API response)
   *
   * @return Map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("stat_year", statYear);
    map.put("yearly_summary", yearlySummary);
    map.put("created_at", createdAt.toString());
    map.put("updated_at", updatedAt.toString());
    return map;
  }

  // Getters

  public TenantYearlyStatisticsIdentifier id() {
    return id;
  }

  public TenantIdentifier tenantId() {
    return tenantId;
  }

  public String statYear() {
    return statYear;
  }

  public Map<String, Object> yearlySummary() {
    return yearlySummary;
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
    private TenantYearlyStatisticsIdentifier id;
    private TenantIdentifier tenantId;
    private String statYear;
    private Map<String, Object> yearlySummary = new HashMap<>();
    private Instant createdAt;
    private Instant updatedAt;

    public Builder id(TenantYearlyStatisticsIdentifier id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(TenantIdentifier tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder statYear(String statYear) {
      this.statYear = statYear;
      return this;
    }

    public Builder yearlySummary(Map<String, Object> yearlySummary) {
      this.yearlySummary = new HashMap<>(yearlySummary);
      return this;
    }

    public Builder addYearlySummaryMetric(String name, Object value) {
      this.yearlySummary.put(name, value);
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

    public TenantYearlyStatistics build() {
      return new TenantYearlyStatistics(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TenantYearlyStatistics that = (TenantYearlyStatistics) o;
    return Objects.equals(id, that.id)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(statYear, that.statYear);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId, statYear);
  }

  @Override
  public String toString() {
    return "TenantYearlyStatistics{"
        + "id="
        + id
        + ", tenantId="
        + tenantId
        + ", statYear="
        + statYear
        + ", yearlySummarySize="
        + yearlySummary.size()
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + '}';
  }
}
