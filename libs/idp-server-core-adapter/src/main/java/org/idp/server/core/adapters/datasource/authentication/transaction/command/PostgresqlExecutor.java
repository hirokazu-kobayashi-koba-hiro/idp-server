package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.authentication.AuthenticationInteractionResult;
import org.idp.server.core.authentication.AuthenticationInteractionType;
import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.device.AuthenticationDevice;
import org.idp.server.core.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationTransactionCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void insert(Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO authentication_transaction (authorization_id, tenant_id, authorization_flow, client_id, user_id, user_payload, authentication_device_id, available_authentication_types, required_any_of_authentication_types, last_interaction_type, interactions, created_at, expired_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?)
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
    if (user.hasAuthenticationDevices()) {
      AuthenticationDevice authenticationDevice = user.findPreferredForNotification();
      params.add(authenticationDevice.id());
    } else {
      params.add(null);
    }
    params.add(
        jsonConverter.write(authenticationTransaction.request().availableAuthenticationTypes()));
    params.add(
        jsonConverter.write(
            authenticationTransaction.request().requiredAnyOfAuthenticationTypes()));
    if (authenticationTransaction.hasInteractions()) {
      AuthenticationInteractionType lastInteractionType =
          authenticationTransaction.lastInteractionType();
      params.add(lastInteractionType.name());
      params.add(jsonConverter.write(authenticationTransaction.interactionResultsAsSet()));
    } else {
      params.add(null);
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
                SET user_id = ?,
                user_payload = ?::jsonb,
                authentication_device_id = ?,
                last_interaction_type = ?,
                interactions = ?::jsonb
                WHERE authorization_id = ?
                AND tenant_id = ?
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
      AuthenticationInteractionResult last = authenticationTransaction.lastInteraction();
      params.add(last.type());
      params.add(jsonConverter.write(authenticationTransaction.interactionResultsAsSet()));
    } else {
      params.add(null);
      params.add(null);
    }

    params.add(authenticationTransaction.identifier().value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationIdentifier identifier) {}
}
