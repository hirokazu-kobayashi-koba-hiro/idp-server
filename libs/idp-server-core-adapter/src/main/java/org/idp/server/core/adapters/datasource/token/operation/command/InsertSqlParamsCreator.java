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

package org.idp.server.core.adapters.datasource.token.operation.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.json.JsonConverter;

class InsertSqlParamsCreator {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static List<Object> create(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    AuthorizationGrant authorizationGrant = oAuthToken.accessToken().authorizationGrant();
    List<Object> params = new ArrayList<>();
    params.add(oAuthToken.identifier().valueAsUuid());
    params.add(oAuthToken.tenantIdentifier().valueAsUuid());
    params.add(oAuthToken.tokenIssuer().value());
    params.add(oAuthToken.tokenType().name());
    params.add(toEncryptedJson(oAuthToken.accessTokenEntity().value(), aesCipher));
    params.add(hmacHasher.hash(oAuthToken.accessTokenEntity().value()));

    if (authorizationGrant.hasUser()) {
      params.add((authorizationGrant.user().subAsUuid()));
      params.add(toJson(authorizationGrant.user()));
    } else {
      params.add(null);
      params.add(null);
    }

    params.add(toJson(authorizationGrant.authentication()));
    params.add(authorizationGrant.requestedClientId().value());
    params.add(toJson(authorizationGrant.client()));
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

    return params;
  }

  private static String toJson(Object value) {
    return jsonConverter.write(value);
  }

  private static String toEncryptedJson(String value, AesCipher aesCipher) {
    EncryptedData encrypted = aesCipher.encrypt(value);
    return toJson(encrypted);
  }
}
