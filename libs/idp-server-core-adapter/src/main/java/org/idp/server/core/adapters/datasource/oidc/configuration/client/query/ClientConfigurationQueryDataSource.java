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

package org.idp.server.core.adapters.datasource.oidc.configuration.client.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.uuid.UuidMatcher;

public class ClientConfigurationQueryDataSource implements ClientConfigurationQueryRepository {

  ClientConfigSqlExecutor executor;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public ClientConfigurationQueryDataSource(
      ClientConfigSqlExecutor executor, JsonConverter jsonConverter, CacheStore cacheStore) {
    this.executor = executor;
    this.jsonConverter = jsonConverter;
    this.cacheStore = cacheStore;
  }

  @Override
  public ClientConfiguration get(Tenant tenant, RequestedClientId requestedClientId) {
    String key = key(tenant.identifier(), requestedClientId.value());
    Optional<ClientConfiguration> optionalClientConfiguration =
        cacheStore.find(key, ClientConfiguration.class);

    if (optionalClientConfiguration.isPresent()) {
      return optionalClientConfiguration.get();
    }

    Map<String, String> resultClientIdAlias = executor.selectByAlias(tenant, requestedClientId);

    if (resultClientIdAlias != null && !resultClientIdAlias.isEmpty()) {

      ClientConfiguration convert = ModelConverter.convert(resultClientIdAlias);
      cacheStore.put(key, convert);
      return convert;
    }

    if (!UuidMatcher.isValid(requestedClientId.value())) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", requestedClientId.value()), tenant);
    }

    Map<String, String> resultClientId =
        executor.selectById(tenant, new ClientIdentifier(requestedClientId.value()));

    if (resultClientId == null || resultClientId.isEmpty()) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", requestedClientId.value()), tenant);
    }

    ClientConfiguration convert = ModelConverter.convert(resultClientId);

    cacheStore.put(key, convert);

    return convert;
  }

  @Override
  public ClientConfiguration get(Tenant tenant, ClientIdentifier clientIdentifier) {
    String key = key(tenant.identifier(), clientIdentifier.value());
    Optional<ClientConfiguration> optionalClientConfiguration =
        cacheStore.find(key, ClientConfiguration.class);

    if (optionalClientConfiguration.isPresent()) {
      return optionalClientConfiguration.get();
    }

    Map<String, String> result = executor.selectById(tenant, clientIdentifier);

    if (result == null || result.isEmpty()) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", clientIdentifier.value()), tenant);
    }

    ClientConfiguration converted = ModelConverter.convert(result);
    cacheStore.put(key, converted);

    return converted;
  }

  @Override
  public List<ClientConfiguration> findList(Tenant tenant, int limit, int offset) {
    List<Map<String, String>> maps = executor.selectList(tenant, limit, offset);

    if (Objects.isNull(maps) || maps.isEmpty()) {
      return List.of();
    }

    return maps.stream().map(ModelConverter::convert).toList();
  }

  @Override
  public List<ClientConfiguration> findList(Tenant tenant, ClientQueries queries) {
    List<Map<String, String>> maps = executor.selectList(tenant, queries);

    if (Objects.isNull(maps) || maps.isEmpty()) {
      return List.of();
    }

    return maps.stream().map(ModelConverter::convert).toList();
  }

  @Override
  public long findTotalCount(Tenant tenant, ClientQueries queries) {
    Map<String, String> result = executor.selectTotalCount(tenant, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public ClientConfiguration find(Tenant tenant, ClientIdentifier clientIdentifier) {
    String key = key(tenant.identifier(), clientIdentifier.value());
    Optional<ClientConfiguration> optionalClientConfiguration =
        cacheStore.find(key, ClientConfiguration.class);

    if (optionalClientConfiguration.isPresent()) {
      return optionalClientConfiguration.get();
    }

    Map<String, String> result = executor.selectById(tenant, clientIdentifier);

    if (result == null || result.isEmpty()) {
      return new ClientConfiguration();
    }

    ClientConfiguration converted = ModelConverter.convert(result);
    cacheStore.put(key, converted);

    return converted;
  }

  @Override
  public ClientConfiguration findWithDisabled(
      Tenant tenant, ClientIdentifier clientIdentifier, boolean includeDisabled) {
    String key = key(tenant.identifier(), clientIdentifier.value());
    Optional<ClientConfiguration> optionalClientConfiguration =
        cacheStore.find(key, ClientConfiguration.class);

    if (optionalClientConfiguration.isPresent()) {
      return optionalClientConfiguration.get();
    }

    Map<String, String> result = executor.selectById(tenant, clientIdentifier, includeDisabled);

    if (result == null || result.isEmpty()) {
      return new ClientConfiguration();
    }

    ClientConfiguration converted = ModelConverter.convert(result);
    cacheStore.put(key, converted);

    return converted;
  }

  private String key(TenantIdentifier tenantIdentifier, String clientId) {
    return "tenantId:"
        + tenantIdentifier.value()
        + ":"
        + ClientConfiguration.class.getSimpleName()
        + ":"
        + clientId;
  }
}
