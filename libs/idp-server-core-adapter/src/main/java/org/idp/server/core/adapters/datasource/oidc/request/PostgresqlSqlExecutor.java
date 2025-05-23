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

package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlSqlExecutor implements AuthorizationRequestSqlExecutor {

  PostgresqlSqlExecutor() {}

  @Override
  public void insert(Tenant tenant, AuthorizationRequest authorizationRequest) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                    INSERT INTO authorization_request
                    (id,
                    tenant_id,
                    profile,
                    scopes,
                    response_type,
                    client_id,
                    client_payload,
                    redirect_uri,
                    state,
                    response_mode,
                    nonce,
                    display,
                    prompts,
                    max_age,
                    ui_locales,
                    id_token_hint,
                    login_hint,
                    acr_values,
                    claims_value,
                    request_object,
                    request_uri,
                    code_challenge,
                    code_challenge_method,
                    authorization_details,
                    custom_params,
                    expires_in,
                    expires_at
                    )
                    VALUES (
                    ?::uuid,
                    ?::uuid,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::jsonb,
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
                    ?::jsonb,
                    ?::jsonb,
                    ?,
                    ?);
                    """;

    List<Object> params = InsertSqlCreator.createInsert(authorizationRequest);
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                SELECT
                id,
                tenant_id,
                profile,
                scopes,
                response_type,
                client_id,
                client_payload,
                redirect_uri,
                state,
                response_mode,
                nonce,
                display,
                prompts,
                max_age,
                ui_locales,
                id_token_hint,
                login_hint,
                acr_values,
                claims_value,
                request_object,
                request_uri,
                code_challenge,
                code_challenge_method,
                authorization_details,
                custom_params,
                expires_in,
                expires_at
                FROM authorization_request
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid;
                """;
    List<Object> params = new ArrayList<>();
    params.add(authorizationRequestIdentifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqpTemplate =
        """
            DELETE FROM authorization_request
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid;
            """;
    List<Object> params = new ArrayList<>();
    params.add(authorizationRequestIdentifier.value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqpTemplate, params);
  }
}
