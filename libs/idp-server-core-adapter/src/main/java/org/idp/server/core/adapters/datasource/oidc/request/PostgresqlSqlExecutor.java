package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

public class PostgresqlSqlExecutor implements AuthorizationRequestSqlExecutor {

  PostgresqlSqlExecutor() {}

  @Override
  public void insert(AuthorizationRequest authorizationRequest) {
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
                    custom_params
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
                    ?::jsonb);
                    """;

    List<Object> params = InsertSqlCreator.createInsert(authorizationRequest);
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                SELECT id,
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
                custom_params
                FROM authorization_request
                WHERE id = ?::uuid;
                """;
    List<Object> params = new ArrayList<>();
    params.add(authorizationRequestIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
