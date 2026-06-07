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

package org.idp.server.core.adapters.datasource.authentication.config.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.exception.AuthenticationConfigurationNotFoundException;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AuthenticationConfigurationQueryDataSource
    implements AuthenticationConfigurationQueryRepository {

  AuthenticationConfigSqlExecutor executor;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public AuthenticationConfigurationQueryDataSource(
      AuthenticationConfigSqlExecutor executor, CacheStore cacheStore) {
    this.executor = executor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.cacheStore = cacheStore;
  }

  @Override
  public AuthenticationConfiguration get(Tenant tenant, String type) {
    String key = typeKey(tenant.identifier(), type);
    Optional<AuthenticationConfiguration> cached =
        cacheStore.find(key, AuthenticationConfiguration.class);
    if (cached.isPresent()) {
      return cached.get();
    }

    Map<String, String> result = executor.selectOne(tenant, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new AuthenticationConfigurationNotFoundException(
          String.format(
              "Authentication Configuration is Not Found (%s) (%s)",
              tenant.identifierValue(), type));
    }

    AuthenticationConfiguration configuration =
        jsonConverter.read(result.get("payload"), AuthenticationConfiguration.class);
    cacheStore.put(key, configuration);
    return configuration;
  }

  @Override
  public AuthenticationConfiguration find(Tenant tenant, String type) {
    String key = typeKey(tenant.identifier(), type);
    Optional<AuthenticationConfiguration> cached =
        cacheStore.find(key, AuthenticationConfiguration.class);
    if (cached.isPresent()) {
      return cached.get();
    }

    Map<String, String> result = executor.selectOne(tenant, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new AuthenticationConfiguration();
    }

    AuthenticationConfiguration configuration =
        jsonConverter.read(result.get("payload"), AuthenticationConfiguration.class);
    cacheStore.put(key, configuration);
    return configuration;
  }

  @Override
  public AuthenticationConfiguration find(
      Tenant tenant, AuthenticationConfigurationIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new AuthenticationConfiguration();
    }

    return jsonConverter.read(result.get("payload"), AuthenticationConfiguration.class);
  }

  @Override
  public AuthenticationConfiguration findWithDisabled(
      Tenant tenant, AuthenticationConfigurationIdentifier identifier, boolean includeDisabled) {
    Map<String, String> result = executor.selectOne(tenant, identifier, includeDisabled);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new AuthenticationConfiguration();
    }

    return jsonConverter.read(result.get("payload"), AuthenticationConfiguration.class);
  }

  @Override
  public long findTotalCount(Tenant tenant) {
    Map<String, String> result = executor.selectCount(tenant);

    if (Objects.isNull(result) || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset) {
    List<Map<String, String>> results = executor.selectList(tenant, limit, offset);

    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }

    return results.stream()
        .map(result -> jsonConverter.read(result.get("payload"), AuthenticationConfiguration.class))
        .toList();
  }

  public static String typeKey(TenantIdentifier tenantIdentifier, String type) {
    return tenantKeyPrefix(tenantIdentifier) + type;
  }

  /**
   * Returns the shared prefix of all {@link AuthenticationConfiguration} cache keys for a tenant.
   *
   * <p>Used by command-side invalidation to drop every cached entry in one shot when an individual
   * key-based invalidation could miss the right entry — e.g. when the {@code type} portion of the
   * key is itself mutated by an update.
   */
  public static String tenantKeyPrefix(TenantIdentifier tenantIdentifier) {
    return "tenantId:"
        + tenantIdentifier.value()
        + ":"
        + AuthenticationConfiguration.class.getSimpleName()
        + ":type:";
  }
}
