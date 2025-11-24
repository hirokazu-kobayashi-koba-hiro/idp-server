package org.idp.server.platform.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Tenant daily statistics data
 *
 * <p>Holds calculated daily statistics with hardcoded metrics in JSONB format.
 *
 * <p>Standard metrics (calculated by DailyStatisticsAggregationService):
 *
 * <ul>
 *   <li>dau - Daily Active Users
 *   <li>login_success_count - Successful logins
 *   <li>login_failure_count - Failed logins
 *   <li>login_success_rate - Success rate percentage
 *   <li>tokens_issued - Total tokens issued
 *   <li>new_users - New user registrations
 *   <li>total_users - Cumulative user count
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * TenantStatisticsData data = TenantStatisticsData.builder()
 *     .tenantId(tenantId)
 *     .statDate(LocalDate.now().minusDays(1))
 *     .addMetric("dau", 1250)
 *     .addMetric("login_success_rate", 97.5)
 *     .addMetric("tokens_issued", 800)
 *     .build();
 *
 * Integer dau = data.getIntegerMetric("dau");
 * Double successRate = data.getNumberMetric("login_success_rate");
 * }</pre>
 */
public class TenantStatisticsData {

  private final TenantStatisticsDataIdentifier id;
  private final TenantIdentifier tenantId;
  private final LocalDate statDate;
  private final Map<String, Object> metrics;
  private final Instant createdAt;

  private TenantStatisticsData(Builder builder) {
    this.id = builder.id;
    this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
    this.statDate = Objects.requireNonNull(builder.statDate, "statDate must not be null");
    this.metrics = Collections.unmodifiableMap(new HashMap<>(builder.metrics));
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
  }

  /**
   * Check if metric exists
   *
   * @param metricName metric name
   * @return true if exists
   */
  public boolean hasMetric(String metricName) {
    return metrics.containsKey(metricName);
  }

  /**
   * Get Integer metric (type-safe)
   *
   * @param name metric name
   * @return Integer value (null if not exists or wrong type)
   */
  public Integer getIntegerMetric(String name) {
    Object value = metrics.get(name);
    if (value instanceof Integer) {
      return (Integer) value;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return null;
  }

  /**
   * Get Long metric (type-safe)
   *
   * @param name metric name
   * @return Long value (null if not exists or wrong type)
   */
  public Long getLongMetric(String name) {
    Object value = metrics.get(name);
    if (value instanceof Long) {
      return (Long) value;
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return null;
  }

  /**
   * Get Double metric (type-safe)
   *
   * @param name metric name
   * @return Double value (null if not exists or wrong type)
   */
  public Double getNumberMetric(String name) {
    Object value = metrics.get(name);
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return null;
  }

  /**
   * Convert to Map (for API response)
   *
   * @return Map representation
   */
  public Map<String, Object> toMap() {
    return Map.of(
        "date", statDate.toString(),
        "metrics", metrics,
        "created_at", createdAt.toString());
  }

  // Getters

  public TenantStatisticsDataIdentifier id() {
    return id;
  }

  public TenantIdentifier tenantId() {
    return tenantId;
  }

  public LocalDate statDate() {
    return statDate;
  }

  public Map<String, Object> metrics() {
    return metrics;
  }

  public Instant createdAt() {
    return createdAt;
  }

  // Builder

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private TenantStatisticsDataIdentifier id;
    private TenantIdentifier tenantId;
    private LocalDate statDate;
    private Map<String, Object> metrics = new HashMap<>();
    private Instant createdAt;

    public Builder id(TenantStatisticsDataIdentifier id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(TenantIdentifier tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder statDate(LocalDate statDate) {
      this.statDate = statDate;
      return this;
    }

    public Builder metrics(Map<String, Object> metrics) {
      this.metrics = new HashMap<>(metrics);
      return this;
    }

    public Builder addMetric(String name, Object value) {
      this.metrics.put(name, value);
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public TenantStatisticsData build() {
      return new TenantStatisticsData(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TenantStatisticsData that = (TenantStatisticsData) o;
    return Objects.equals(id, that.id)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(statDate, that.statDate)
        && Objects.equals(metrics, that.metrics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId, statDate, metrics);
  }

  @Override
  public String toString() {
    return "TenantStatisticsData{"
        + "id="
        + id
        + ", tenantId="
        + tenantId
        + ", statDate="
        + statDate
        + ", metricsCount="
        + metrics.size()
        + ", createdAt="
        + createdAt
        + '}';
  }
}
