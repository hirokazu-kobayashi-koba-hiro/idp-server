package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.core.federation.FederationConfigurationIdentifier;
import org.idp.server.core.federation.FederationConfigurationNotFoundException;
import org.idp.server.core.federation.FederationType;
import org.idp.server.core.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.federation.sso.SsoProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class FederationConfigurationQueryDataSource
    implements FederationConfigurationQueryRepository {

  FederationConfigurationSqlExecutors executors;
  JsonConverter jsonConverter;

  public FederationConfigurationQueryDataSource() {
    this.executors = new FederationConfigurationSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(
      Tenant tenant, FederationType federationType, SsoProvider ssoProvider, Class<T> clazz) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, federationType, ssoProvider);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new FederationConfigurationNotFoundException(
          String.format(
              "federation configuration is not found (%s) (%s) (%s)",
              tenant.identifierValue(), federationType.name(), ssoProvider.name()));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }

  @Override
  public FederationConfiguration find(Tenant tenant, FederationConfigurationIdentifier identifier) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new FederationConfiguration();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<FederationConfiguration> findList(Tenant tenant, int limit, int offset) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());

    List<Map<String, String>> results = executor.selectList(tenant, limit, offset);

    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).collect(Collectors.toList());
  }
}
