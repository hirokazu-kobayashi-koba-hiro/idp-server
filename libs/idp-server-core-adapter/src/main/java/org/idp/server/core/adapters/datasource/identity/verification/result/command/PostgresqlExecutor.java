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

package org.idp.server.core.adapters.datasource.identity.verification.result.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationResultCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, IdentityVerificationResult result) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO public.identity_verification_result
                    (
                    id,
                    tenant_id,
                    user_id,
                    application_id,
                    verification_type,
                    verified_claims,
                    verified_at,
                    valid_until,
                    source,
                    source_details)
                    VALUES (
                    ?::uuid,
                    ?::uuid,
                    ?::uuid,
                    ?::uuid,
                    ?,
                    ?::jsonb,
                    ?,
                    ?,
                    ?,
                    ?::jsonb
                    );
                """;

    List<Object> params = new ArrayList<>();
    params.add(result.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(result.userId().valueAsUuid());
    if (result.hasApplicationId()) {
      params.add(result.applicationId().valueAsUuid());
    } else {
      params.add(null);
    }
    params.add(result.identityVerificationType().name());
    params.add(jsonConverter.write(result.verifiedClaims().toMap()));
    params.add(result.verifiedAt());
    if (result.hasVerifiedUntil()) {
      params.add(result.verifiedUntil());
    } else {
      params.add(null);
    }
    params.add(result.source().value());
    if (result.hasSourceDetails()) {
      params.add(jsonConverter.write(result.sourceDetails().toMap()));
    } else {
      params.add(null);
    }

    sqlExecutor.execute(sqlTemplate, params);
  }
}
