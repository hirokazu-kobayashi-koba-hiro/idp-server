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

package org.idp.server.core.adapters.datasource.oidc.configuration.server.command;

import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AuthorizationServerConfigurationCommandDataSource
    implements AuthorizationServerConfigurationCommandRepository {

  ServerConfigSqlExecutor executor;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public AuthorizationServerConfigurationCommandDataSource(
      ServerConfigSqlExecutor executor, JsonConverter jsonConverter, CacheStore cacheStore) {
    this.executor = executor;
    this.jsonConverter = jsonConverter;
    this.cacheStore = cacheStore;
  }

  @Override
  public void register(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    executor.insert(tenant, authorizationServerConfiguration);
  }

  @Override
  public void update(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    executor.update(tenant, authorizationServerConfiguration);

    String key = key(tenant.identifier());
    cacheStore.delete(key);
  }

  private String key(TenantIdentifier tenantIdentifier) {
    return "tenantId:"
        + tenantIdentifier.value()
        + ":"
        + AuthorizationServerConfiguration.class.getSimpleName();
  }
}
