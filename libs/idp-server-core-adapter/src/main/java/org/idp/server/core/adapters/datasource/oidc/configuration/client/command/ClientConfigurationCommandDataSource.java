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

package org.idp.server.core.adapters.datasource.oidc.configuration.client.command;

import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ClientConfigurationCommandDataSource implements ClientConfigurationCommandRepository {

  ClientConfigCommandSqlExecutors executors;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public ClientConfigurationCommandDataSource(CacheStore cacheStore) {
    this.executors = new ClientConfigCommandSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.cacheStore = cacheStore;
  }

  @Override
  public void register(Tenant tenant, ClientConfiguration clientConfiguration) {
    ClientConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, clientConfiguration);
  }

  @Override
  public void update(Tenant tenant, ClientConfiguration clientConfiguration) {
    ClientConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, clientConfiguration);
    String key = key(tenant.identifier(), clientConfiguration.clientIdentifier().value());
    cacheStore.delete(key);
    if (clientConfiguration.clientIdAlias() != null) {
      String aliasKey = key(tenant.identifier(), clientConfiguration.clientIdAlias());
      cacheStore.delete(aliasKey);
    }
  }

  @Override
  public void delete(Tenant tenant, ClientConfiguration clientConfiguration) {
    ClientConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, clientConfiguration.clientIdentifier());
    String key = key(tenant.identifier(), clientConfiguration.clientIdentifier().value());
    cacheStore.delete(key);
    if (clientConfiguration.clientIdAlias() != null) {
      String aliasKey = key(tenant.identifier(), clientConfiguration.clientIdAlias());
      cacheStore.delete(aliasKey);
    }
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
