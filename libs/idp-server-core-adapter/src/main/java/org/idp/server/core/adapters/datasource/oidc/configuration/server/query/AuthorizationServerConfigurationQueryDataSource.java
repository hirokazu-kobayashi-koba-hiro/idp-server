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

package org.idp.server.core.adapters.datasource.oidc.configuration.server.query;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AuthorizationServerConfigurationQueryDataSource
    implements AuthorizationServerConfigurationQueryRepository {

  ServerConfigSqlExecutor executor;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public AuthorizationServerConfigurationQueryDataSource(
      ServerConfigSqlExecutor executor, JsonConverter jsonConverter, CacheStore cacheStore) {
    this.executor = executor;
    this.jsonConverter = jsonConverter;
    this.cacheStore = cacheStore;
  }

  @Override
  public AuthorizationServerConfiguration get(Tenant tenant) {
    String key = key(tenant.identifier());
    Optional<AuthorizationServerConfiguration> authorizationServerConfiguration =
        cacheStore.find(key, AuthorizationServerConfiguration.class);

    if (authorizationServerConfiguration.isPresent()) {

      return authorizationServerConfiguration.get();
    }

    Map<String, String> stringMap = executor.selectOne(tenant.identifier());

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new ServerConfigurationNotFoundException(
          String.format("unregistered server configuration (%s)", tenant.identifierValue()),
          tenant);
    }

    AuthorizationServerConfiguration convert = ModelConverter.convert(stringMap);

    cacheStore.put(key, convert);

    return convert;
  }

  @Override
  public AuthorizationServerConfiguration getWithDisabled(Tenant tenant, boolean includeDisabled) {
    Map<String, String> stringMap = executor.selectOne(tenant.identifier(), includeDisabled);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new ServerConfigurationNotFoundException(
          String.format("unregistered server configuration (%s)", tenant.identifierValue()),
          tenant);
    }

    return ModelConverter.convert(stringMap);
  }

  private String key(TenantIdentifier tenantIdentifier) {
    return "tenantId:"
        + tenantIdentifier.value()
        + ":"
        + AuthorizationServerConfiguration.class.getSimpleName();
  }
}
