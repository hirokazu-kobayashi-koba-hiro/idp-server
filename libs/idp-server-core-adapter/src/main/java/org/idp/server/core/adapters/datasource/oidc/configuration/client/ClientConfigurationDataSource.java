package org.idp.server.core.adapters.datasource.oidc.configuration.client;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationRepository;

public class ClientConfigurationDataSource implements ClientConfigurationRepository {

  ClientConfigSqlExecutors executors;
  JsonConverter jsonConverter;

  public ClientConfigurationDataSource() {
    this.executors = new ClientConfigSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, ClientConfiguration clientConfiguration) {
    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, clientConfiguration);
  }

  @Override
  public ClientConfiguration get(Tenant tenant, RequestedClientId requestedClientId) {
    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> resultClientIdAlias = executor.selectByAlias(tenant, requestedClientId);

    if (resultClientIdAlias != null && !resultClientIdAlias.isEmpty()) {
      return ModelConverter.convert(resultClientIdAlias);
    }

    Map<String, String> resultClientId =
        executor.selectById(tenant, new ClientIdentifier(requestedClientId.value()));

    if (resultClientId == null || resultClientId.isEmpty()) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", requestedClientId.value()));
    }

    return ModelConverter.convert(resultClientId);
  }

  @Override
  public ClientConfiguration get(Tenant tenant, ClientIdentifier clientIdentifier) {
    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectById(tenant, clientIdentifier);

    if (result == null || result.isEmpty()) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", clientIdentifier.value()));
    }

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
  }

  @Override
  public void delete(Tenant tenant, RequestedClientId requestedClientId) {
    ClientConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, requestedClientId);
  }
}
