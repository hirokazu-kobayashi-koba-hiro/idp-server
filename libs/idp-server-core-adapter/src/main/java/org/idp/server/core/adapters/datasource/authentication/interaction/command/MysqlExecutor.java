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

package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements AuthenticationInteractionCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public <T> void insert(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO authentication_interactions (
            authentication_transaction_id,
            tenant_id,
            interaction_type,
            payload
            )
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
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type, T payload) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE authentication_interactions
                SET payload = ?,
                updated_at = now()
                WHERE authentication_transaction_id = ?
                AND tenant_id = ?
                AND interaction_type = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(payload));
    params.add(identifier.value());
    params.add(tenant.identifierValue());
    params.add(type);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AuthenticationTransactionIdentifier identifier, String type) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM authentication_interactions
                WHERE authentication_transaction_id = ?
                AND tenant_id = ?
                AND interaction_type = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());
    params.add(type);

    sqlExecutor.execute(sqlTemplate, params);
  }
}
