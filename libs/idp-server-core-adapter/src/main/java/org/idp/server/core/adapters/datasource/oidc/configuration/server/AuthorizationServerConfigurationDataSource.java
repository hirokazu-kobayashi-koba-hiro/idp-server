package org.idp.server.core.adapters.datasource.oidc.configuration.server;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.exception.ServerConfigurationNotFoundException;

public class AuthorizationServerConfigurationDataSource
    implements AuthorizationServerConfigurationRepository {

  ServerConfigSqlExecutors executors;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public AuthorizationServerConfigurationDataSource(CacheStore cacheStore) {
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
  public AuthorizationServerConfiguration get(Tenant tenant) {
    String key = key(tenant.identifier());
    Optional<AuthorizationServerConfiguration> authorizationServerConfiguration =
        cacheStore.find(key, AuthorizationServerConfiguration.class);

    if (authorizationServerConfiguration.isPresent()) {

      return authorizationServerConfiguration.get();
    }

    ServerConfigSqlExecutor executor = executors.get(tenant.databaseType());

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

  private String key(TenantIdentifier tenantIdentifier) {
    return "tenantId:"
        + tenantIdentifier.value()
        + ":"
        + AuthorizationServerConfiguration.class.getSimpleName();
  }
}
