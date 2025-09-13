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
import java.util.List;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;

public class MysqlExecutor implements OAuthTokenSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
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
                                ?,
                                ?,
                                ?,
                                ?
                                );
                              """;
    AuthorizationGrant authorizationGrant = oAuthToken.accessToken().authorizationGrant();
    List<Object> params = new ArrayList<>();
    params.add(oAuthToken.identifier().value());
    params.add(oAuthToken.tenantIdentifier().value());
    params.add(oAuthToken.tokenIssuer().value());
    params.add(oAuthToken.tokenType().name());
    params.add(toEncryptedJson(oAuthToken.accessTokenEntity().value(), aesCipher));
    params.add(hmacHasher.hash(oAuthToken.accessTokenEntity().value()));
    if (oAuthToken.hasCustomClaims()) {
      params.add(jsonConverter.write(oAuthToken.accessToken().customClaims().toMap()));
    } else {
      params.add(null);
    }

    if (authorizationGrant.hasUser()) {
      params.add((authorizationGrant.user().sub()));
      params.add(toJson(authorizationGrant.user()));
    } else {
      params.add(null);
      params.add(null);
    }

    params.add(toJson(authorizationGrant.authentication()));
    params.add(authorizationGrant.requestedClientId().value());
    params.add(toJson(authorizationGrant.clientAttributes()));
    params.add(authorizationGrant.grantType().name());
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

    params.add(oAuthToken.accessToken().expiresIn().toStringValue());
    params.add(oAuthToken.accessToken().expiresAt().toLocalDateTime());
    params.add(oAuthToken.accessToken().createdAt().toLocalDateTime());

    if (oAuthToken.hasRefreshToken()) {
      params.add(toEncryptedJson(oAuthToken.refreshTokenEntity().value(), aesCipher));
      params.add(hmacHasher.hash(oAuthToken.refreshTokenEntity().value()));
      params.add(oAuthToken.refreshToken().createdAt().toLocalDateTime());
      params.add(oAuthToken.refreshToken().expiresAt().toLocalDateTime());
    } else {
      params.add(null);
      params.add(null);
      params.add(null);
      params.add(null);
    }

    if (oAuthToken.hasIdToken()) {
      params.add(oAuthToken.idToken().value());
    } else {
      params.add("");
    }
    if (oAuthToken.hasClientCertification()) {
      params.add(oAuthToken.accessToken().clientCertificationThumbprint().value());
    } else {
      params.add("");
    }

    if (oAuthToken.hasCNonce()) {
      params.add(oAuthToken.cNonce().value());
    } else {
      params.add("");
    }

    if (oAuthToken.hasCNonceExpiresIn()) {
      params.add(oAuthToken.cNonceExpiresIn().toStringValue());
    } else {
      params.add("");
    }
    params.add(oAuthToken.expiresAt().toLocalDateTime());
    sqlExecutor.execute(sqlTemplate, params);
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
    String sqlTemplate = """
            DELETE FROM oauth_token WHERE id = ?;
            """;
    List<Object> params = List.of(oAuthToken.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
