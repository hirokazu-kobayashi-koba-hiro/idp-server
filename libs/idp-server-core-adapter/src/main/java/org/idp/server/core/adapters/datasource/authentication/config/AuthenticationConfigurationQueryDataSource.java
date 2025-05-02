package org.idp.server.core.adapters.datasource.authentication.config;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.authentication.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.authentication.exception.AuthenticationConfigurationNotFoundException;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigurationQueryDataSource
    implements AuthenticationConfigurationQueryRepository {

  AuthenticationConfigSqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationConfigurationQueryDataSource() {
    this.executors = new AuthenticationConfigSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
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
}
