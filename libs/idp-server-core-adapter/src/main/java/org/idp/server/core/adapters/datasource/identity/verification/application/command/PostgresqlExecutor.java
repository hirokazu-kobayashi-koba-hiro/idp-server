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
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationApplicationCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, IdentityVerificationApplication application) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO public.identity_verification_applications
                    (id,
                    tenant_id,
                    client_id,
                    user_id,
                    verification_type,
                    application_details,
                    external_workflow_delegation,
                    external_application_id,
                    external_application_details,
                    trust_framework,
                    processes,
                    status,
                    requested_at)
                    VALUES (
                    ?::uuid,
                    ?::uuid,
                    ?,
                    ?::uuid,
                    ?,
                    ?::jsonb,
                    ?,
                    ?,
                    ?::jsonb,
                    ?,
                    ?::jsonb,
                    ?,
                    ?
                    );
                """;

    List<Object> params = new ArrayList<>();
    params.add(application.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(application.requestedClientId().value());
    params.add(application.userIdentifier().valueAsUuid());
    params.add(application.identityVerificationType().name());
    params.add(jsonConverter.write(application.applicationDetails().toMap()));

    params.add(application.externalWorkflowDelegation().name());
    params.add(application.externalApplicationId().value());
    if (application.externalApplicationDetails() != null) {
      params.add(jsonConverter.write(application.externalApplicationDetails().toMap()));
    } else {
      params.add(null);
    }

    if (application.hasTrustFramework()) {
      params.add(application.trustFramework().name());
    } else {
      params.add(null);
    }

    params.add(jsonConverter.write(application.processesAsList()));
    params.add(application.status().value());
    params.add(application.requestedAt().toString());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, IdentityVerificationApplication application) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlBuilder = new StringBuilder();
    List<Object> params = new ArrayList<>();

    sqlBuilder.append("UPDATE identity_verification_applications SET ");

    List<String> setClauses = new ArrayList<>();
    setClauses.add("application_details = ?::jsonb");
    params.add(jsonConverter.write(application.applicationDetails().toMap()));

    if (application.hasTrustFramework()) {
      setClauses.add("trust_framework = ?");
      params.add(application.trustFramework().name());
    }

    setClauses.add("status = ?");
    params.add(application.status().value());

    if (application.hasExternalApplicationDetails()) {
      setClauses.add("external_application_details = ?::jsonb");
      params.add(jsonConverter.write(application.externalApplicationDetails().toMap()));
    }

    if (application.hasExaminationResults()) {
      setClauses.add("examination_results = ?::jsonb");
      params.add(jsonConverter.write(application.examinationResultsAsMapList()));
    }

    setClauses.add("processes = ?::jsonb");
    params.add(jsonConverter.write(application.processesAsList()));

    setClauses.add("updated_at = now()");
    sqlBuilder.append(String.join(", ", setClauses));

    sqlBuilder.append(" WHERE id = ?::uuid AND tenant_id = ?::uuid");
    params.add(application.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlBuilder.toString(), params);
  }

  @Override
  public void delete(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            DELETE FROM identity_verification_applications
            WHERE tenant_id = ?::uuid
            AND user_id = ?::uuid
            AND id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(user.subAsUuid());
    params.add(identifier.valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
