package org.idp.server.core.adapters.datasource.federation.config.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements FederationConfigurationSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, FederationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO federation_configurations (
            id,
            tenant_id,
            type,
            sso_provider,
            payload
            ) VALUES (
            ?,
            ?,
            ?,
            ?,
            ?
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.identifier().value());
    params.add(tenant.identifier().value());
    params.add(configuration.type().name());
    params.add(configuration.ssoProvider().name());
    params.add(jsonConverter.write(configuration.payload()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, FederationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE federation_configurations
            SET payload = ?
            WHERE id = ?
            AND tenant_id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(configuration.payload()));
    params.add(configuration.identifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, FederationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM federation_configurations
            WHERE id = ?
            AND tenant_id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.identifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
