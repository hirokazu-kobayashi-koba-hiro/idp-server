package org.idp.server.core.adapters.datasource.federation.session.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.federation.sso.SsoSessionIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements SsoSessionCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public <T> void insert(Tenant tenant, SsoSessionIdentifier identifier, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                INSERT INTO federation_sso_session (
                id,
                tenant_id,
                payload
                )
                VALUES (
                ?,
                ?,
                ?
                )
                ON DUPLICATE KEY
                UPDATE payload = ?, updated_at = now();
                """;

    String json = jsonConverter.write(payload);
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(json);
    params.add(json);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, SsoSessionIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM federation_sso_session
                WHERE id = ?
                AND tenant_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
