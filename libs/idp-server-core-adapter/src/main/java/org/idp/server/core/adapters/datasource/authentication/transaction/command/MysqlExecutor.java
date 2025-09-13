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
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements AuthenticationTransactionCommandSqlExecutor {

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
            tenant_payload,
            flow,
            authorization_id,
            client_id,
            client_payload,
            user_id,
            user_payload,
            context,
            authentication_device_id,
            authentication_device_payload,
            authentication_policy,
            interactions,
            attributes,
            created_at,
            expires_at
            )
            VALUES
            (
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?
            )
            ON DUPLICATE KEY UPDATE id=id;
            """;

    User user = authenticationTransaction.user();
    List<Object> params = new ArrayList<>();
    params.add(authenticationTransaction.identifier().value());
    params.add(tenant.identifier().value());
    params.add(jsonConverter.write(tenant.attributes().toMap()));
    params.add(authenticationTransaction.request().authFlow().name());
    if (authenticationTransaction.hasAuthorizationIdentifier()) {
      params.add(authenticationTransaction.authorizationIdentifier().value());
    } else {
      params.add(null);
    }
    params.add(authenticationTransaction.request().requestedClientId().value());
    params.add(jsonConverter.write(authenticationTransaction.request().clientAttributes().toMap()));

    if (user.exists()) {
      params.add(user.sub());
      params.add(jsonConverter.write(user));
    } else {
      params.add(null);
      params.add(null);
    }
    params.add(jsonConverter.write(authenticationTransaction.requestContext().toMap()));
    if (authenticationTransaction.hasAuthenticationDevice()) {
      AuthenticationDevice authenticationDevice = authenticationTransaction.authenticationDevice();
      params.add(authenticationDevice.id());
      params.add(jsonConverter.write(authenticationDevice.toMap()));
    } else {
      params.add(null);
      params.add(null);
    }
    params.add(jsonConverter.write(authenticationTransaction.authenticationPolicy().toMap()));
    if (authenticationTransaction.hasInteractions()) {
      params.add(jsonConverter.write(authenticationTransaction.interactionResultsAsMapObject()));
    } else {
      params.add(null);
    }

    if (authenticationTransaction.hasAttributes()) {
      params.add(jsonConverter.write(authenticationTransaction.attributes().toMap()));
    } else {
      params.add(null);
    }

    params.add(authenticationTransaction.request().createdAt());
    params.add(authenticationTransaction.request().expiredAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE authentication_transaction
                SET user_id = ?,
                user_payload = ?,
                authentication_device_id = ?,
                authentication_device_payload = ?,
                interactions = ?,
                updated_at = now()
                WHERE id = ?
                AND tenant_id = ?
                """;

    User user = authenticationTransaction.user();
    List<Object> params = new ArrayList<>();
    if (authenticationTransaction.hasUser()) {
      params.add(user.sub());
      params.add(jsonConverter.write(user));
    } else {
      params.add(null);
      params.add(null);
    }

    if (authenticationTransaction.hasAuthenticationDevice()) {
      AuthenticationDevice authenticationDevice = authenticationTransaction.authenticationDevice();
      params.add(authenticationDevice.id());
      params.add(jsonConverter.write(authenticationDevice.toMap()));
    } else {
      params.add(null);
      params.add(null);
    }

    if (authenticationTransaction.hasInteractions()) {
      params.add(jsonConverter.write(authenticationTransaction.interactionResultsAsMapObject()));
    } else {
      params.add(null);
    }

    params.add(authenticationTransaction.identifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AuthenticationTransactionIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM authentication_transaction
        WHERE id = ?
        AND tenant_id = ?
    """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
