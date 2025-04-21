package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.oauth.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationTransactionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(Tenant tenant, AuthorizationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT  authorization_id, tenant_id, authorization_flow, client_id, user_id, user_payload, authentication_device_id, available_authentication_types, required_any_of_authentication_types, created_at, expired_at
            FROM authentication_transaction
            WHERE authorization_id = ?
            AND tenant_id = ?
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
        """
                SELECT  authorization_id, tenant_id, authorization_flow, client_id, user_id, user_payload, authentication_device_id, available_authentication_types, required_any_of_authentication_types, created_at, expired_at
                FROM authentication_transaction
                WHERE authentication_device_id = ?
                AND tenant_id = ?
                limit 1
                """;
    List<Object> params = new ArrayList<>();
    params.add(authenticationDeviceIdentifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
