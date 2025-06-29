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

package org.idp.server.core.adapters.datasource.identity.verification.config.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationConfigCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO identity_verification_configuration (
            id,
            tenant_id,
            type,
            payload
            )
            VALUES (
            ?::uuid,
            ?::uuid,
            ?,
            ?::jsonb
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.idAsUuid());
    params.add(tenant.identifierUUID());
    params.add(type.name());
    params.add(jsonConverter.write(configuration));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            UPDATE identity_verification_configuration
            SET payload = ?::jsonb
            WHERE id = ?::uuid
            AND type = ?
            AND tenant_id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(configuration));
    params.add(configuration.idAsUuid());
    params.add(type.name());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM identity_verification_configuration
            WHERE tenant_id = ?::uuid
            AND type = ?
            AND id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(type.name());
    params.add(configuration.idAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
