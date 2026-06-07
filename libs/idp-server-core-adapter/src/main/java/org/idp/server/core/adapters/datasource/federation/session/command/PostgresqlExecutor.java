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

package org.idp.server.core.adapters.datasource.federation.session.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.federation.sso.SsoSessionIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements SsoSessionCommandSqlExecutor {

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
                ?::uuid,
                ?::uuid,
                ?::jsonb
                )
                ON CONFLICT (id) DO
                UPDATE SET payload = ?::jsonb, updated_at = now();
                """;

    String json = jsonConverter.write(payload);
    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifierUUID());
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
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifierUUID());
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public int deleteExpired(int limit) {
    // federation_sso_session has no expires_at column; SSO sessions complete within minutes,
    // so anything older than 1 hour is abandoned.
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM federation_sso_session
            WHERE ctid IN (
              SELECT ctid FROM federation_sso_session
              WHERE created_at < (now() - interval '1 hour')
              LIMIT ?
            );
            """;
    List<Object> params = new ArrayList<>();
    params.add(limit);

    return sqlExecutor.executeAndReturnAffectedRows(sqlTemplate, params);
  }
}
