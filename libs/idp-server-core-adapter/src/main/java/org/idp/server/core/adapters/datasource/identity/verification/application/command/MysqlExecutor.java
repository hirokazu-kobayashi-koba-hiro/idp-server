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

package org.idp.server.core.adapters.datasource.identity.verification.application.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements IdentityVerificationApplicationCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, IdentityVerificationApplication application) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO identity_verification_application
                    (
                    id,
                    tenant_id,
                    client_id,
                    user_id,
                    verification_type,
                    application_details,
                    processes,
                    attributes,
                    status,
                    requested_at)
                    VALUES (
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
                    );
                """;

    List<Object> params = new ArrayList<>();
    params.add(application.identifier().value());
    params.add(tenant.identifier().value());
    params.add(application.requestedClientId().value());
    params.add(application.userIdentifier().value());
    params.add(application.identityVerificationType().name());
    params.add(application.applicationDetails().toJson());
    params.add(jsonConverter.write(application.processesAsMapObject()));
    if (application.hasAttributes()) {
      params.add(application.attributes().toJson());
    } else {
      params.add(null);
    }
    params.add(application.status().value());
    params.add(application.requestedAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, IdentityVerificationApplication application) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlBuilder = new StringBuilder();
    List<Object> params = new ArrayList<>();

    sqlBuilder.append("UPDATE identity_verification_application SET ");

    List<String> setClauses = new ArrayList<>();
    setClauses.add("application_details = ?");
    params.add(jsonConverter.write(application.applicationDetails().toMap()));

    setClauses.add("status = ?");
    params.add(application.status().value());
    setClauses.add("processes = ?");
    params.add(jsonConverter.write(application.processesAsMapObject()));

    setClauses.add("updated_at = now()");
    sqlBuilder.append(String.join(", ", setClauses));

    sqlBuilder.append(" WHERE id = ? AND tenant_id = ?");
    params.add(application.identifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlBuilder.toString(), params);
  }

  @Override
  public void delete(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            DELETE FROM identity_verification_application
            WHERE tenant_id = ?
            AND user_id = ?
            AND id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(user.sub());
    params.add(identifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
