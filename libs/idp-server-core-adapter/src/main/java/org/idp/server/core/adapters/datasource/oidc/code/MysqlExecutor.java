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

package org.idp.server.core.adapters.datasource.oidc.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.oauth.type.oauth.AuthorizationCode;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements AuthorizationCodeGrantExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  MysqlExecutor() {}

  public void insert(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO authorization_code_grant
                    (
                    authorization_request_id,
                    tenant_id,
                    authorization_code,
                    user_id,
                    user_payload,
                    authentication,
                    client_id,
                    client_payload,
                    grant_type,
                    scopes,
                    id_token_claims,
                    userinfo_claims,
                    custom_properties,
                    authorization_details,
                    expires_at,
                    consent_claims
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
                    ?
                    );
                    """;
    List<Object> params = new ArrayList<>();

    params.add(authorizationCodeGrant.authorizationRequestIdentifier().value());
    params.add(authorizationCodeGrant.tenantIdentifier().value());
    params.add(authorizationCodeGrant.authorizationCode().value());
    params.add(authorizationCodeGrant.user().sub());
    params.add(toJson(authorizationCodeGrant.user()));
    params.add(toJson(authorizationCodeGrant.authentication()));
    params.add(authorizationCodeGrant.clientId().value());
    params.add(toJson(authorizationCodeGrant.clientAttributes()));
    params.add(authorizationCodeGrant.authorizationGrant().grantType().name());
    params.add(authorizationCodeGrant.scopes().toStringValues());

    if (authorizationCodeGrant.authorizationGrant().hasIdTokenClaims()) {
      params.add(authorizationCodeGrant.authorizationGrant().idTokenClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationCodeGrant.authorizationGrant().hasUserinfoClaim()) {
      params.add(authorizationCodeGrant.authorizationGrant().userinfoClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationCodeGrant.authorizationGrant().hasCustomProperties()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().customProperties().values()));
    } else {
      params.add("{}");
    }

    if (authorizationCodeGrant.authorizationGrant().hasAuthorizationDetails()) {
      params.add(
          toJson(authorizationCodeGrant.authorizationGrant().authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    params.add(authorizationCodeGrant.expiredAt().toLocalDateTime());

    if (authorizationCodeGrant.authorizationGrant().hasConsentClaims()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().consentClaims().toMap()));
    } else {
      params.add(null);
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  public Map<String, String> selectOne(Tenant tenant, AuthorizationCode authorizationCode) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                SELECT
                authorization_request_id,
                tenant_id,
                authorization_code,
                user_id,
                user_payload,
                authentication,
                client_id,
                client_payload,
                grant_type,
                scopes,
                id_token_claims,
                userinfo_claims,
                custom_properties,
                authorization_details,
                expires_at,
                consent_claims
                FROM authorization_code_grant
                WHERE authorization_code = ?
                AND tenant_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(authorizationCode.value());
    params.add(tenant.identifier().value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  public void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                DELETE FROM authorization_code_grant
                WHERE authorization_request_id = ?
                AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(authorizationCodeGrant.authorizationRequestIdentifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
