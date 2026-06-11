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

package org.idp.server.core.adapters.datasource.token.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;

public class PostgresqlExecutor implements OAuthTokenSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public Map<String, String> insert(
      OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                         INSERT INTO oauth_token (
                            id,
                            tenant_id,
                            token_issuer,
                            token_type,
                            encrypted_access_token,
                            hashed_access_token,
                            access_token_custom_claims,
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
                            expires_in,
                            access_token_expires_at,
                            access_token_created_at,
                            encrypted_refresh_token,
                            hashed_refresh_token,
                            refresh_token_created_at,
                            refresh_token_expires_at,
                            id_token,
                            client_certification_thumbprint,
                            c_nonce,
                            c_nonce_expires_in,
                            expires_at
                            )
                            VALUES (
                            ?::uuid,
                            ?::uuid,
                            ?,
                            ?,
                            ?::jsonb,
                            ?,
                            ?::jsonb,
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
                            ?
                            );
                          """;

    List<Object> params = new ArrayList<>();
    Map<String, String> row = buildParamsAndRow(oAuthToken, aesCipher, hmacHasher, params);

    sqlExecutor.execute(sqlTemplate, params);
    return row;
  }

  /**
   * Builds the INSERT params and the cache-shaped row in lockstep, without touching the database,
   * so the cache can be warmed without an extra SELECT. The row's key set is guarded against the
   * query-side SELECT columns by {@code OAuthTokenInsertRowParityTest}.
   */
  Map<String, String> buildParamsAndRow(
      OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher, List<Object> params) {
    AuthorizationGrant authorizationGrant = oAuthToken.accessToken().authorizationGrant();
    Map<String, String> row = new LinkedHashMap<>();

    OAuthTokenRowBuilder.add(params, row, "id", oAuthToken.identifier().valueAsUuid());
    OAuthTokenRowBuilder.add(params, row, "tenant_id", oAuthToken.tenantIdentifier().valueAsUuid());
    OAuthTokenRowBuilder.add(params, row, "token_issuer", oAuthToken.tokenIssuer().value());
    OAuthTokenRowBuilder.add(params, row, "token_type", oAuthToken.tokenType().name());
    OAuthTokenRowBuilder.add(
        params,
        row,
        "encrypted_access_token",
        toEncryptedJson(oAuthToken.accessTokenEntity().value(), aesCipher));
    OAuthTokenRowBuilder.add(
        params,
        row,
        "hashed_access_token",
        hmacHasher.hash(oAuthToken.accessTokenEntity().value()));
    if (oAuthToken.hasCustomClaims()) {
      OAuthTokenRowBuilder.add(
          params,
          row,
          "access_token_custom_claims",
          jsonConverter.write(oAuthToken.accessToken().customClaims().toMap()));
    } else {
      OAuthTokenRowBuilder.add(params, row, "access_token_custom_claims", (Object) null);
    }

    if (authorizationGrant.hasUser()) {
      OAuthTokenRowBuilder.add(params, row, "user_id", authorizationGrant.user().subAsUuid());
      OAuthTokenRowBuilder.add(params, row, "user_payload", toJson(authorizationGrant.user()));
    } else {
      OAuthTokenRowBuilder.add(params, row, "user_id", (Object) null);
      OAuthTokenRowBuilder.add(params, row, "user_payload", (Object) null);
    }

    OAuthTokenRowBuilder.add(
        params, row, "authentication", toJson(authorizationGrant.authentication()));
    OAuthTokenRowBuilder.add(
        params, row, "client_id", authorizationGrant.requestedClientId().value());
    OAuthTokenRowBuilder.add(
        params, row, "client_payload", toJson(authorizationGrant.clientAttributes()));
    OAuthTokenRowBuilder.add(params, row, "grant_type", authorizationGrant.grantType().name());
    OAuthTokenRowBuilder.add(params, row, "scopes", authorizationGrant.scopes().toStringValues());

    if (authorizationGrant.hasIdTokenClaims()) {
      OAuthTokenRowBuilder.add(
          params, row, "id_token_claims", authorizationGrant.idTokenClaims().toStringValues());
    } else {
      OAuthTokenRowBuilder.add(params, row, "id_token_claims", "");
    }

    if (authorizationGrant.hasUserinfoClaim()) {
      OAuthTokenRowBuilder.add(
          params, row, "userinfo_claims", authorizationGrant.userinfoClaims().toStringValues());
    } else {
      OAuthTokenRowBuilder.add(params, row, "userinfo_claims", "");
    }

    if (authorizationGrant.hasCustomProperties()) {
      OAuthTokenRowBuilder.add(
          params, row, "custom_properties", toJson(authorizationGrant.customProperties().values()));
    } else {
      OAuthTokenRowBuilder.add(params, row, "custom_properties", "{}");
    }

    if (authorizationGrant.hasAuthorizationDetails()) {
      OAuthTokenRowBuilder.add(
          params,
          row,
          "authorization_details",
          toJson(authorizationGrant.authorizationDetails().toMapValues()));
    } else {
      OAuthTokenRowBuilder.add(params, row, "authorization_details", "[]");
    }

    OAuthTokenRowBuilder.add(
        params, row, "expires_in", oAuthToken.accessToken().expiresIn().toStringValue());
    OAuthTokenRowBuilder.add(
        params,
        row,
        "access_token_expires_at",
        oAuthToken.accessToken().expiresAt().toLocalDateTime());
    OAuthTokenRowBuilder.add(
        params,
        row,
        "access_token_created_at",
        oAuthToken.accessToken().createdAt().toLocalDateTime());

    if (oAuthToken.hasRefreshToken()) {
      OAuthTokenRowBuilder.add(
          params,
          row,
          "encrypted_refresh_token",
          toEncryptedJson(oAuthToken.refreshTokenEntity().value(), aesCipher));
      OAuthTokenRowBuilder.add(
          params,
          row,
          "hashed_refresh_token",
          hmacHasher.hash(oAuthToken.refreshTokenEntity().value()));
      OAuthTokenRowBuilder.add(
          params,
          row,
          "refresh_token_created_at",
          oAuthToken.refreshToken().createdAt().toLocalDateTime());
      OAuthTokenRowBuilder.add(
          params,
          row,
          "refresh_token_expires_at",
          oAuthToken.refreshToken().expiresAt().toLocalDateTime());
    } else {
      OAuthTokenRowBuilder.add(params, row, "encrypted_refresh_token", (Object) null);
      OAuthTokenRowBuilder.add(params, row, "hashed_refresh_token", (Object) null);
      OAuthTokenRowBuilder.add(params, row, "refresh_token_created_at", (Object) null);
      OAuthTokenRowBuilder.add(params, row, "refresh_token_expires_at", (Object) null);
    }

    if (oAuthToken.hasIdToken()) {
      OAuthTokenRowBuilder.add(params, row, "id_token", oAuthToken.idToken().value());
    } else {
      OAuthTokenRowBuilder.add(params, row, "id_token", "");
    }
    if (oAuthToken.hasClientCertification()) {
      OAuthTokenRowBuilder.add(
          params,
          row,
          "client_certification_thumbprint",
          oAuthToken.accessToken().clientCertificationThumbprint().value());
    } else {
      OAuthTokenRowBuilder.add(params, row, "client_certification_thumbprint", "");
    }

    if (oAuthToken.hasCNonce()) {
      OAuthTokenRowBuilder.add(params, row, "c_nonce", oAuthToken.cNonce().value());
    } else {
      OAuthTokenRowBuilder.add(params, row, "c_nonce", "");
    }

    if (oAuthToken.hasCNonceExpiresIn()) {
      OAuthTokenRowBuilder.add(
          params, row, "c_nonce_expires_in", oAuthToken.cNonceExpiresIn().toStringValue());
    } else {
      OAuthTokenRowBuilder.add(params, row, "c_nonce_expires_in", "");
    }
    OAuthTokenRowBuilder.add(params, row, "expires_at", oAuthToken.expiresAt().toLocalDateTime());

    return row;
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }

  private String toEncryptedJson(String value, AesCipher aesCipher) {
    EncryptedData encrypted = aesCipher.encrypt(value);
    return toJson(encrypted);
  }

  @Override
  public void delete(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM oauth_token
            WHERE id = ?::uuid;
            """;
    List<Object> params = List.of(oAuthToken.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public List<String> selectHashedAccessTokensByUserAndClient(
      String tenantId, String userId, String clientId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            SELECT hashed_access_token FROM oauth_token
            WHERE tenant_id = ?::uuid
              AND user_id = ?::uuid
              AND client_id = ?;
            """;
    List<Object> params = List.of(tenantId, userId, clientId);
    List<Map<String, String>> results = sqlExecutor.selectList(sqlTemplate, params);
    return results.stream().map(row -> row.get("hashed_access_token")).toList();
  }

  @Override
  public void deleteByUserAndClient(String tenantId, String userId, String clientId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM oauth_token
            WHERE tenant_id = ?::uuid
              AND user_id = ?::uuid
              AND client_id = ?;
            """;
    List<Object> params = List.of(tenantId, userId, clientId);

    sqlExecutor.execute(sqlTemplate, params);
  }
}
