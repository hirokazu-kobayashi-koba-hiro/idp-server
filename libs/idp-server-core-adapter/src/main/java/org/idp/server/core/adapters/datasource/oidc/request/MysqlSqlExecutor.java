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
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.CustomParams;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlSqlExecutor implements AuthorizationRequestSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  MysqlSqlExecutor() {}

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
                    ?);
                    """;

    List<Object> params = new ArrayList<>();
    params.add(authorizationRequest.identifier().value());
    params.add(authorizationRequest.tenantIdentifier().value());
    params.add(authorizationRequest.profile().name());
    params.add(authorizationRequest.scopes().toStringValues());
    params.add(authorizationRequest.responseType().name());
    params.add(authorizationRequest.requestedClientId().value());
    params.add(toJson(authorizationRequest.clientAttributes()));

    if (authorizationRequest.hasRedirectUri()) {
      params.add(authorizationRequest.redirectUri().value());
    } else {
      params.add("");
    }

    if (authorizationRequest.hasState()) {
      params.add(authorizationRequest.state().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasResponseMode()) {
      params.add(authorizationRequest.responseMode().name());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasNonce()) {
      params.add(authorizationRequest.nonce().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasDisplay()) {
      params.add(authorizationRequest.display().name());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasPrompts()) {
      params.add(authorizationRequest.prompts().toStringValues());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasMaxAge()) {
      params.add(authorizationRequest.maxAge().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasUilocales()) {
      params.add(authorizationRequest.uiLocales().toStringValues());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasIdTokenHint()) {
      params.add(authorizationRequest.idTokenHint().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasLoginHint()) {
      params.add(authorizationRequest.loginHint().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasAcrValues()) {
      params.add(authorizationRequest.acrValues().toStringValues());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasClaims()) {
      params.add(authorizationRequest.claims().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasRequest()) {
      params.add(authorizationRequest.request().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasRequestUri()) {
      params.add(authorizationRequest.requestUri().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasCodeChallenge()) {
      params.add(authorizationRequest.codeChallenge().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasCodeChallengeMethod()) {
      params.add(authorizationRequest.codeChallengeMethod().name());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasAuthorizationDetails()) {
      params.add(convertJsonAuthorizationDetails(authorizationRequest.authorizationDetails()));
    } else {
      params.add("[]");
    }

    if (authorizationRequest.hasCustomParams()) {
      params.add(convertJsonCustomParams(authorizationRequest.customParams()));
    } else {
      params.add("{}");
    }
    params.add(authorizationRequest.expiresIn().toStringValue());
    params.add(authorizationRequest.expiredAt().toLocalDateTime());
    sqlExecutor.execute(sqlTemplate, params);
  }

  private String convertJsonAuthorizationDetails(AuthorizationDetails authorizationDetails) {

    return toJson(authorizationDetails.toMapValues());
  }

  private String convertJsonCustomParams(CustomParams customParams) {

    return toJson(customParams.values());
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
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
                WHERE id = ?
                AND tenant_id = ?;
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
            WHERE id = ?
            AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(authorizationRequestIdentifier.value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqpTemplate, params);
  }
}
