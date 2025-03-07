package org.idp.server.core.handler.federation.datasource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.federation.FederatableIdProviderConfiguration;
import org.idp.server.core.federation.FederatableIdProviderConfigurationNotFoundException;
import org.idp.server.core.federation.FederatableIdProviderConfigurationRepository;

public class FederatableIdProviderConfigurationDataSource
    implements FederatableIdProviderConfigurationRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public FederatableIdProviderConfiguration get(String identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                SELECT id, payload FROM federatable_idp_configuration WHERE identifier = ?;
                """;
    List<Object> params = List.of(identifier);
    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result)) {
      throw new FederatableIdProviderConfigurationNotFoundException(
          String.format("federatable idp configuration is not found (%s)", identifier));
    }

    return jsonConverter.read(result.get("payload"), FederatableIdProviderConfiguration.class);
  }
}
