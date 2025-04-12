package org.idp.server.core.adapters.datasource.federation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.federation.FederationConfigurationNotFoundException;
import org.idp.server.core.federation.FederationConfigurationQueryRepository;
import org.idp.server.core.federation.FederationType;
import org.idp.server.core.federation.SsoProvider;
import org.idp.server.core.tenant.TenantIdentifier;

public class FederationConfigurationQueryDataSource
    implements FederationConfigurationQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> T get(
      TenantIdentifier tenantIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider,
      Class<T> clazz) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                SELECT id, payload FROM federation_configurations
                WHERE tenant_id = ?
                AND type = ?
                AND sso_provider_name = ?
                """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.value());
    params.add(federationType.name());
    params.add(ssoProvider.name());

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new FederationConfigurationNotFoundException(
          String.format(
              "federation configuration is not found (%s) (%s) (%s)",
              tenantIdentifier.value(), federationType.name(), ssoProvider.name()));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}
