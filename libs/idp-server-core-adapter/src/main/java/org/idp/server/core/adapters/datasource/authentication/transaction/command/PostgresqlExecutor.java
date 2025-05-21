package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.authentication.AuthenticationTransaction;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationTransactionCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO authentication_transaction
            (
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
            )
            VALUES
            (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            ?::uuid,
            ?::jsonb,
            ?::jsonb,
            ?::uuid,
            ?::jsonb,
            ?::jsonb,
            ?,
            ?
            )
            ON CONFLICT DO NOTHING;
            """;

    User user = authenticationTransaction.user();
    List<Object> params = new ArrayList<>();
    params.add(authenticationTransaction.identifier().value());
    params.add(tenant.identifierValue());
    params.add(authenticationTransaction.request().authorizationFlow().value());
    params.add(authenticationTransaction.request().requestedClientId().value());
    params.add(user.sub());
    params.add(jsonConverter.write(user));
    params.add(jsonConverter.write(authenticationTransaction.requestContext().toMap()));
    if (user.hasAuthenticationDevices()) {
      AuthenticationDevice authenticationDevice = user.findPreferredForNotification();
      params.add(authenticationDevice.id());
    } else {
      params.add(null);
    }
    params.add(jsonConverter.write(authenticationTransaction.authenticationPolicy().toMap()));
    if (authenticationTransaction.hasInteractions()) {
      params.add(jsonConverter.write(authenticationTransaction.interactionResultsAsMap()));
    } else {
      params.add(null);
    }

    params.add(authenticationTransaction.request().createdAt().toString());
    params.add(authenticationTransaction.request().expiredAt().toString());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE authentication_transaction
                SET user_id = ?::uuid,
                user_payload = ?::jsonb,
                authentication_device_id = ?::uuid,
                interactions = ?::jsonb
                WHERE authorization_id = ?::uuid
                AND tenant_id = ?::uuid
                """;

    User user = authenticationTransaction.user();
    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(jsonConverter.write(user));

    if (user.hasAuthenticationDevices()) {
      AuthenticationDevice authenticationDevice = user.findPreferredForNotification();
      params.add(authenticationDevice.id());
    } else {
      params.add(null);
    }

    if (authenticationTransaction.hasInteractions()) {
      params.add(jsonConverter.write(authenticationTransaction.interactionResultsAsMap()));
    } else {
      params.add(null);
    }

    params.add(authenticationTransaction.identifier().value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationIdentifier identifier) {}
}
