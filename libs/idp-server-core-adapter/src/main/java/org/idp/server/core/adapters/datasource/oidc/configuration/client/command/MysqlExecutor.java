package org.idp.server.core.adapters.datasource.oidc.configuration.client.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class MysqlExecutor implements ClientConfigCommandSqlExecutor {

  JsonConverter jsonConverter;

  public MysqlExecutor() {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void insert(Tenant tenant, ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO client_configuration (id, id_alias, tenant_id, payload)
            VALUES (?, ?, ?, ?)
            """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientIdentifier().value());
    params.add(clientConfiguration.clientIdAlias());
    params.add(tenant.identifierValue());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE client_configuration
                SET id_alias = ?,
                payload = ?
                WHERE tenant_id = ?
                AND id = ?
                """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientIdAlias());
    params.add(payload);
    params.add(tenant.identifierValue());
    params.add(clientConfiguration.clientIdentifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, ClientIdentifier clientIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM client_configuration
                WHERE tenant_id = ?
                AND id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(clientIdentifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
