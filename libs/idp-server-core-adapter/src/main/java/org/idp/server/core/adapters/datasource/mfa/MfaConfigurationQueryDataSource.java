package org.idp.server.core.adapters.datasource.mfa;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.mfa.MfaConfigurationQueryRepository;
import org.idp.server.core.mfa.exception.MfaConfigurationNotFoundException;
import org.idp.server.core.tenant.Tenant;

public class MfaConfigurationQueryDataSource implements MfaConfigurationQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> T get(Tenant tenant, String key, Class<T> clazz) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            SELECT id, payload
            FROM mfa_configuration
            WHERE tenant_id = ?
            AND key = ?
            """;

    List<Object> params = List.of(tenant.identifierValue());
    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new MfaConfigurationNotFoundException(
          String.format("Mfa Configuration is Not Found (%s) (%s)", tenant.identifierValue(), key));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}
