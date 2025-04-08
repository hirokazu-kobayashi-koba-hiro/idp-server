package org.idp.server.core.adapters.datasource.security;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.security.hook.ssf.SharedSignalFrameworkConfiguration;
import org.idp.server.core.security.hook.ssf.SharedSignalFrameworkConfigurationRepository;

public class SharedSignalFrameworkConfigurationDataSource
    implements SharedSignalFrameworkConfigurationRepository {

  JsonConverter converter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public SharedSignalFrameworkConfiguration find(String issuer) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                SELECT id, payload FROM shared_signal_framework_configuration WHERE id = ?;
                """;
    List<Object> params = List.of(issuer);

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (result == null || result.isEmpty()) {
      return new SharedSignalFrameworkConfiguration();
    }

    return converter.read(result.get("payload"), SharedSignalFrameworkConfiguration.class);
  }
}
