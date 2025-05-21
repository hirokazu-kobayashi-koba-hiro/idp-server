package org.idp.server.core.adapters.datasource.authentication.config.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.core.oidc.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.core.oidc.authentication.exception.AuthenticationConfigurationNotFoundException;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigurationQueryDataSource
    implements AuthenticationConfigurationQueryRepository {

  AuthenticationConfigSqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationConfigurationQueryDataSource() {
    this.executors = new AuthenticationConfigSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(Tenant tenant, String type, Class<T> clazz) {
    AuthenticationConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new AuthenticationConfigurationNotFoundException(
          String.format(
              "Mfa Configuration is Not Found (%s) (%s)", tenant.identifierValue(), type));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }

  @Override
  public AuthenticationConfiguration find(
      Tenant tenant, AuthenticationConfigurationIdentifier identifier) {
    AuthenticationConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new AuthenticationConfiguration();
    }

    String id = result.get("id");
    String type = result.get("type");
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(result.get("payload"));

    return new AuthenticationConfiguration(id, type, jsonNodeWrapper.toMap());
  }

  @Override
  public List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset) {
    return List.of();
  }
}
