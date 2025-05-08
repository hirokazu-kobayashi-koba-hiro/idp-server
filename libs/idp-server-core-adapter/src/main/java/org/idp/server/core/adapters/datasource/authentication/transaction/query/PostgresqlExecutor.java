package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationTransactionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(Tenant tenant, AuthorizationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + " "
            + """
            WHERE authorization_id = ?::uuid
            AND tenant_id = ?::uuid
            """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + " "
            + """
                WHERE authentication_device_id = ?::uuid
                AND tenant_id = ?::uuid
                ORDER BY created_at DESC
                limit 1
                """;
    List<Object> params = new ArrayList<>();
    params.add(authenticationDeviceIdentifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  String selectSql =
      """
          SELECT
          authorization_id,
          tenant_id,
          authorization_flow,
          client_id,
          user_id,
          user_payload,
          context,
          authentication_device_id,
          authentication_policy,
          interactions,
          created_at,
          expired_at
          FROM authentication_transaction
          """;
}
