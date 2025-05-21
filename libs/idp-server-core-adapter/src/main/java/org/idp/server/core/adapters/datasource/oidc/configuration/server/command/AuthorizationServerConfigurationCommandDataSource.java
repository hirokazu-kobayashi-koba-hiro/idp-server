package org.idp.server.core.adapters.datasource.oidc.configuration.server.command;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationCommandRepository;

public class AuthorizationServerConfigurationCommandDataSource
    implements AuthorizationServerConfigurationCommandRepository {

  ServerConfigSqlExecutors executors;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public AuthorizationServerConfigurationCommandDataSource(CacheStore cacheStore) {
    this.executors = new ServerConfigSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.cacheStore = cacheStore;
  }

  @Override
  public void register(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    ServerConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, authorizationServerConfiguration);
  }

  @Override
  public void update(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    ServerConfigSqlExecutor executor = executors.get(tenant.databaseType());
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
