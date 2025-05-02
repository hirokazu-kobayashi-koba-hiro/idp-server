package org.idp.server.core.adapters.datasource.federation.config;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.federation.FederationConfigurationNotFoundException;
import org.idp.server.core.federation.FederationConfigurationQueryRepository;
import org.idp.server.core.federation.FederationType;
import org.idp.server.core.federation.SsoProvider;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class FederationConfigurationQueryDataSource
    implements FederationConfigurationQueryRepository {

  FederationConfigurationQuerySqlExecutors executors;
  JsonConverter jsonConverter;

  public FederationConfigurationQueryDataSource() {
    this.executors = new FederationConfigurationQuerySqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(
      Tenant tenant, FederationType federationType, SsoProvider ssoProvider, Class<T> clazz) {
    FederationConfigurationQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, federationType, ssoProvider);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new FederationConfigurationNotFoundException(
          String.format(
              "federation configuration is not found (%s) (%s) (%s)",
              tenant.identifierValue(), federationType.name(), ssoProvider.name()));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}
