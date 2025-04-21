package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.tenant.Tenant;

public class MysqlExecutor implements AuthenticationInteractionCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public <T> void insert(
      Tenant tenant, AuthorizationIdentifier identifier, String type, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO authentication_interactions (authorization_id, tenant_id, interaction_type, payload)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE payload = ?, updated_at = now()
            """;

    String json = jsonConverter.write(payload);

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());
    params.add(type);
    params.add(json);
    params.add(json);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public <T> void update(
      Tenant tenant, AuthorizationIdentifier identifier, String type, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE authentication_interactions
                SET payload = ?,
                updated_at = now()
                WHERE id = ?
                AND tenant_id =
                AND type = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(payload));
    params.add(identifier.value());
    params.add(tenant.identifierValue());
    params.add(type);

    sqlExecutor.execute(sqlTemplate, params);
  }
}
