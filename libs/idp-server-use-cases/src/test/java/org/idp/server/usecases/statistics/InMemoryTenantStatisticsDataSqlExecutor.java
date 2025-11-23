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

package org.idp.server.usecases.statistics;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.TenantStatisticsData;
import org.idp.server.platform.statistics.TenantStatisticsDataIdentifier;

public class InMemoryTenantStatisticsDataSqlExecutor
    implements org.idp.server.core.adapters.datasource.statistics.command
            .TenantStatisticsDataSqlExecutor,
        org.idp.server.core.adapters.datasource.statistics.query.TenantStatisticsDataSqlExecutor {

  private final Map<String, TenantStatisticsData> storage = new ConcurrentHashMap<>();
  private final JsonConverter jsonConverter = JsonConverter.defaultInstance();

  @Override
  public void insert(TenantStatisticsData data) {
    String key = generateKey(data.tenantId(), data.statDate());
    storage.put(key, data);
  }

  @Override
  public void update(TenantStatisticsData data) {
    String key = generateKey(data.tenantId(), data.statDate());
    storage.put(key, data);
  }

  @Override
  public void upsert(TenantStatisticsData data) {
    String key = generateKey(data.tenantId(), data.statDate());
    storage.put(key, data);
  }

  @Override
  public void delete(TenantStatisticsDataIdentifier id) {
    storage.entrySet().removeIf(entry -> entry.getValue().id().equals(id));
  }

  @Override
  public void deleteByDate(TenantIdentifier tenantId, LocalDate date) {
    String key = generateKey(tenantId, date);
    storage.remove(key);
  }

  @Override
  public void deleteOlderThan(LocalDate before) {
    storage.entrySet().removeIf(entry -> entry.getValue().statDate().isBefore(before));
  }

  @Override
  public void deleteByTenantId(TenantIdentifier tenantId) {
    storage
        .entrySet()
        .removeIf(entry -> entry.getValue().tenantId().value().equals(tenantId.value()));
  }

  @Override
  public void incrementMetric(
      TenantIdentifier tenantId, LocalDate date, String metricName, int increment) {
    String key = generateKey(tenantId, date);
    TenantStatisticsData existing = storage.get(key);

    if (existing == null) {
      TenantStatisticsData newData =
          TenantStatisticsData.builder()
              .id(new TenantStatisticsDataIdentifier(UUID.randomUUID()))
              .tenantId(tenantId)
              .statDate(date)
              .addMetric(metricName, increment)
              .build();
      storage.put(key, newData);
    } else {
      Map<String, Object> metrics = new HashMap<>(existing.metrics());
      int currentValue = (Integer) metrics.getOrDefault(metricName, 0);
      metrics.put(metricName, currentValue + increment);
      TenantStatisticsData updated =
          TenantStatisticsData.builder()
              .id(existing.id())
              .tenantId(existing.tenantId())
              .statDate(existing.statDate())
              .metrics(metrics)
              .build();
      storage.put(key, updated);
    }
  }

  // Query Executor methods

  @Override
  public Map<String, String> selectOne(TenantStatisticsDataIdentifier id) {
    return storage.values().stream()
        .filter(data -> data.id().equals(id))
        .findFirst()
        .map(this::toMap)
        .orElse(null);
  }

  @Override
  public Map<String, String> selectByDate(TenantIdentifier tenantId, LocalDate date) {
    String key = generateKey(tenantId, date);
    TenantStatisticsData data = storage.get(key);
    return data != null ? toMap(data) : null;
  }

  @Override
  public List<Map<String, String>> selectByDateRange(
      TenantIdentifier tenantId, LocalDate from, LocalDate to) {
    return storage.values().stream()
        .filter(
            data ->
                data.tenantId().value().equals(tenantId.value())
                    && !data.statDate().isBefore(from)
                    && !data.statDate().isAfter(to))
        .sorted((a, b) -> b.statDate().compareTo(a.statDate()))
        .map(this::toMap)
        .toList();
  }

  @Override
  public Map<String, String> selectCount(TenantIdentifier tenantId, LocalDate from, LocalDate to) {
    long count =
        storage.values().stream()
            .filter(
                data ->
                    data.tenantId().value().equals(tenantId.value())
                        && !data.statDate().isBefore(from)
                        && !data.statDate().isAfter(to))
            .count();
    return Map.of("count", String.valueOf(count));
  }

  @Override
  public Map<String, String> selectLatest(TenantIdentifier tenantId) {
    return storage.values().stream()
        .filter(data -> data.tenantId().value().equals(tenantId.value()))
        .max(Comparator.comparing(TenantStatisticsData::statDate))
        .map(this::toMap)
        .orElse(null);
  }

  @Override
  public Map<String, String> selectExists(TenantIdentifier tenantId, LocalDate date) {
    String key = generateKey(tenantId, date);
    long count = storage.containsKey(key) ? 1 : 0;
    return Map.of("count", String.valueOf(count));
  }

  // Helper methods for testing

  public TenantStatisticsData findByDate(TenantIdentifier tenantId, LocalDate date) {
    String key = generateKey(tenantId, date);
    return storage.get(key);
  }

  public void clear() {
    storage.clear();
  }

  private String generateKey(TenantIdentifier tenantId, LocalDate date) {
    return tenantId.value() + ":" + date.toString();
  }

  private Map<String, String> toMap(TenantStatisticsData data) {
    Map<String, String> result = new HashMap<>();
    result.put("id", data.id().value());
    result.put("tenant_id", data.tenantId().value());
    result.put("stat_date", data.statDate().toString());
    result.put("metrics", jsonConverter.write(data.metrics()));
    result.put("created_at", data.createdAt().toString());
    return result;
  }
}
