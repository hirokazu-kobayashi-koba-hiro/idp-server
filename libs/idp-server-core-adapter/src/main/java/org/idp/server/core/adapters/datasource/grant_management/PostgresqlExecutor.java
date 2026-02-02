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

package org.idp.server.core.adapters.datasource.grant_management;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueries;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class PostgresqlExecutor implements AuthorizationGrantedSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(AuthorizationGranted authorizationGranted) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                        INSERT INTO authorization_granted
                        (id,
                        tenant_id,
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
                        consent_claims
                        )
                        VALUES (
                        ?::uuid,
                        ?::uuid,
                        ?::uuid,
                        ?::jsonb,
                        ?::jsonb,
                        ?,
                        ?::jsonb,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?::jsonb,
                        ?::jsonb,
                        ?::jsonb);
                        """;
    List<Object> params = new ArrayList<>();

    AuthorizationGrant authorizationGrant = authorizationGranted.authorizationGrant();
    params.add(authorizationGranted.identifier().valueAsUuid());
    params.add(authorizationGrant.tenantIdentifier().valueAsUuid());
    params.add(authorizationGrant.user().subAsUuid());
    params.add(toJson(authorizationGrant.user()));
    params.add(toJson(authorizationGrant.authentication()));
    params.add(authorizationGrant.requestedClientId().value());
    params.add(toJson(authorizationGrant.clientAttributes()));
    params.add(authorizationGrant.grantType().value());
    params.add(authorizationGrant.scopes().toStringValues());

    if (authorizationGrant.hasIdTokenClaims()) {
      params.add(authorizationGrant.idTokenClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasUserinfoClaim()) {
      params.add(authorizationGrant.userinfoClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasCustomProperties()) {
      params.add(toJson(authorizationGrant.customProperties().values()));
    } else {
      params.add("{}");
    }

    if (authorizationGrant.hasAuthorizationDetails()) {
      params.add(toJson(authorizationGrant.authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    if (authorizationGrant.hasConsentClaims()) {
      params.add(toJson(authorizationGrant.consentClaims().toMap()));
    } else {
      params.add(null);
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
              SELECT
              id,
              tenant_id,
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
              consent_claims
              FROM authorization_granted
              WHERE tenant_id = ?::uuid
              AND client_id = ?
              AND user_id = ?::uuid
              ORDER BY updated_at DESC
              limit 1;
              """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.valueAsUuid());
    params.add(requestedClientId.value());
    params.add(user.subAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, AuthorizationGranted authorizationGranted) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE authorization_granted
                SET user_payload = ?::jsonb,
                authentication = ?::jsonb,
                client_payload = ?::jsonb,
                grant_type = ?,
                scopes = ?,
                id_token_claims = ?,
                userinfo_claims = ?,
                custom_properties = ?::jsonb,
                authorization_details = ?::jsonb,
                consent_claims = ?::jsonb,
                updated_at = now()
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    AuthorizationGrant authorizationGrant = authorizationGranted.authorizationGrant();
    params.add(toJson(authorizationGrant.user()));
    params.add(toJson(authorizationGrant.authentication()));
    params.add(toJson(authorizationGrant.clientAttributes()));
    params.add(authorizationGrant.grantType().value());
    params.add(authorizationGrant.scopes().toStringValues());

    if (authorizationGrant.hasIdTokenClaims()) {
      params.add(authorizationGrant.idTokenClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasUserinfoClaim()) {
      params.add(authorizationGrant.userinfoClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasCustomProperties()) {
      params.add(toJson(authorizationGrant.customProperties().values()));
    } else {
      params.add("{}");
    }

    if (authorizationGrant.hasAuthorizationDetails()) {
      params.add(toJson(authorizationGrant.authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    if (authorizationGrant.hasConsentClaims()) {
      params.add(toJson(authorizationGrant.consentClaims().toMap()));
    } else {
      params.add(null);
    }

    params.add(authorizationGranted.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectById(
      TenantIdentifier tenantIdentifier, AuthorizationGrantedIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT
            id,
            tenant_id,
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
            consent_claims,
            created_at,
            updated_at
            FROM authorization_granted
            WHERE tenant_id = ?::uuid
            AND id = ?::uuid;
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.valueAsUuid());
    params.add(identifier.valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(
      TenantIdentifier tenantIdentifier, AuthorizationGrantedQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql = new StringBuilder();
    sql.append(
        """
            SELECT
            id,
            tenant_id,
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
            consent_claims,
            created_at,
            updated_at
            FROM authorization_granted
            WHERE tenant_id = ?::uuid
            """);

    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.valueAsUuid());

    if (queries.hasUserId()) {
      sql.append(" AND user_id = ?::uuid");
      params.add(queries.userId());
    }

    if (queries.hasClientId()) {
      sql.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    sql.append(" ORDER BY updated_at DESC");
    sql.append(" LIMIT ? OFFSET ?");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sql.toString(), params);
  }

  @Override
  public long selectCount(TenantIdentifier tenantIdentifier, AuthorizationGrantedQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT COUNT(*) as count FROM authorization_granted WHERE tenant_id = ?::uuid");

    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.valueAsUuid());

    if (queries.hasUserId()) {
      sql.append(" AND user_id = ?::uuid");
      params.add(queries.userId());
    }

    if (queries.hasClientId()) {
      sql.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    Map<String, String> result = sqlExecutor.selectOne(sql.toString(), params);
    return Long.parseLong(result.get("count"));
  }

  @Override
  public void delete(TenantIdentifier tenantIdentifier, AuthorizationGrantedIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            DELETE FROM authorization_granted
            WHERE tenant_id = ?::uuid
            AND id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.valueAsUuid());
    params.add(identifier.valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
