/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.authentication.AuthenticationTransaction;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
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
            id,
            tenant_id,
            flow,
            authorization_id,
            client_id,
            user_id,
            user_payload,
            context,
            authentication_device_id,
            authentication_policy,
            interactions,
            attributes,
            created_at,
            expired_at
            )
            VALUES
            (
            ?::uuid,
            ?::uuid,
            ?,
            ?::uuid,
            ?,
            ?::uuid,
            ?::jsonb,
            ?::jsonb,
            ?::uuid,
            ?::jsonb,
            ?::jsonb,
            ?::jsonb,
            ?,
            ?
            )
            ON CONFLICT DO NOTHING;
            """;

    User user = authenticationTransaction.user();
    List<Object> params = new ArrayList<>();
    params.add(authenticationTransaction.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(authenticationTransaction.request().authorizationFlow().value());
    params.add(authenticationTransaction.authorizationIdentifier().valueAsUuid());
    params.add(authenticationTransaction.request().requestedClientId().value());

    if (user.exists()) {
      params.add(user.subAsUuid());
      params.add(jsonConverter.write(user));
    } else {
      params.add(null);
      params.add(null);
    }
    params.add(jsonConverter.write(authenticationTransaction.requestContext().toMap()));
    if (user.hasAuthenticationDevices()) {
      AuthenticationDevice authenticationDevice = user.findPreferredForNotification();
      params.add(authenticationDevice.idAsUuid());
    } else {
      params.add(null);
    }
    params.add(jsonConverter.write(authenticationTransaction.authenticationPolicy().toMap()));
    if (authenticationTransaction.hasInteractions()) {
      params.add(jsonConverter.write(authenticationTransaction.interactionResultsAsMap()));
    } else {
      params.add(null);
    }

    if (authenticationTransaction.hasAttributes()) {
      params.add(jsonConverter.write(authenticationTransaction.attributes().toMap()));
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
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid
                """;

    User user = authenticationTransaction.user();
    List<Object> params = new ArrayList<>();
    params.add(user.subAsUuid());
    params.add(jsonConverter.write(user));

    if (user.hasAuthenticationDevices()) {
      AuthenticationDevice authenticationDevice = user.findPreferredForNotification();
      params.add(authenticationDevice.idAsUuid());
    } else {
      params.add(null);
    }

    if (authenticationTransaction.hasInteractions()) {
      params.add(jsonConverter.write(authenticationTransaction.interactionResultsAsMap()));
    } else {
      params.add(null);
    }

    params.add(authenticationTransaction.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AuthenticationTransactionIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM authentication_transaction
        WHERE id = ?::uuid
        AND tenant_id = ?::uuid
    """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(identifier.valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
