package org.idp.server.core.adapters.datasource.oidc.configuration.client;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.uuid.UuidMatcher;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationRepository;

public class ClientConfigurationDataSource implements ClientConfigurationRepository {

  ClientConfigSqlExecutors executors;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public ClientConfigurationDataSource(CacheStore cacheStore) {
    this.executors = new ClientConfigSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.cacheStore = cacheStore;
  }

  @Override
  public void register(Tenant tenant, ClientConfiguration clientConfiguration) {
    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, clientConfiguration);
    String key = key(tenant.identifier(), clientConfiguration.clientIdentifier().value());
    cacheStore.delete(key);
    if (clientConfiguration.clientIdAlias() != null) {
      String aliasKey = key(tenant.identifier(), clientConfiguration.clientIdAlias());
      cacheStore.delete(aliasKey);
    }
  }

  @Override
  public ClientConfiguration get(Tenant tenant, RequestedClientId requestedClientId) {
    String key = key(tenant.identifier(), requestedClientId.value());
    Optional<ClientConfiguration> optionalClientConfiguration =
        cacheStore.find(key, ClientConfiguration.class);

    if (optionalClientConfiguration.isPresent()) {
      return optionalClientConfiguration.get();
    }

    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> resultClientIdAlias = executor.selectByAlias(tenant, requestedClientId);

    if (resultClientIdAlias != null && !resultClientIdAlias.isEmpty()) {

      ClientConfiguration convert = ModelConverter.convert(resultClientIdAlias);
      cacheStore.put(key, convert, 300);
      return convert;
    }

    if (!UuidMatcher.isValid(requestedClientId.value())) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", requestedClientId.value()));
    }

    Map<String, String> resultClientId =
        executor.selectById(tenant, new ClientIdentifier(requestedClientId.value()));

    if (resultClientId == null || resultClientId.isEmpty()) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", requestedClientId.value()));
    }

    ClientConfiguration convert = ModelConverter.convert(resultClientId);

    cacheStore.put(key, convert, 300);

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

    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectById(tenant, clientIdentifier);

    if (result == null || result.isEmpty()) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", clientIdentifier.value()));
    }

    cacheStore.put(key, result, 300);

    return ModelConverter.convert(result);
  }

  @Override
  public List<ClientConfiguration> find(Tenant tenant, int limit, int offset) {
    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> maps = executor.selectList(tenant, limit, offset);

    if (Objects.isNull(maps) || maps.isEmpty()) {
      return List.of();
    }

    return maps.stream().map(ModelConverter::convert).toList();
  }

  @Override
  public void update(Tenant tenant, ClientConfiguration clientConfiguration) {
    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, clientConfiguration);
    String key = key(tenant.identifier(), clientConfiguration.clientIdentifier().value());
    cacheStore.delete(key);
    if (clientConfiguration.clientIdAlias() != null) {
      String aliasKey = key(tenant.identifier(), clientConfiguration.clientIdAlias());
      cacheStore.delete(aliasKey);
    }
  }

  @Override
  public void delete(Tenant tenant, RequestedClientId requestedClientId) {
    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, requestedClientId);
    String key = key(tenant.identifier(), requestedClientId.value());
    cacheStore.delete(key);
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
