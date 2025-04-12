package org.idp.server.core.adapters.datasource.authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.authentication.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.authentication.exception.MfaConfigurationNotFoundException;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.tenant.Tenant;

public class AuthenticationConfigurationQueryDataSource
    implements AuthenticationConfigurationQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> T get(Tenant tenant, String type, Class<T> clazz) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            SELECT id, payload
            FROM authentication_configuration
            WHERE tenant_id = ?
            AND type = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(type);

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new MfaConfigurationNotFoundException(
          String.format(
              "Mfa Configuration is Not Found (%s) (%s)", tenant.identifierValue(), type));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}
