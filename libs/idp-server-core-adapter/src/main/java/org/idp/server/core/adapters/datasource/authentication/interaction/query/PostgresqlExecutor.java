package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationInteractionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            SELECT authorization_id, payload
            FROM authentication_interactions
            WHERE authorization_id = ?
            AND tenant_id = ?
            AND interaction_type = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());
    params.add(type);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
