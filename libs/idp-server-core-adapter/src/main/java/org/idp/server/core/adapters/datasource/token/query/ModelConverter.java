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

package org.idp.server.core.adapters.datasource.token.query;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.EncryptedData;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.extension.ExpiresAt;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.IdToken;
import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.client.Client;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.GrantIdTokenClaims;
import org.idp.server.core.oidc.grant.GrantUserinfoClaims;
import org.idp.server.core.oidc.grant.consent.ConsentClaims;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.token.AccessToken;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.OAuthTokenBuilder;
import org.idp.server.core.oidc.token.OAuthTokenIdentifier;
import org.idp.server.core.oidc.token.RefreshToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  // TODO refactor
  static OAuthToken convert(Map<String, String> stringMap, AesCipher aesCipher) {
    OAuthTokenIdentifier id = new OAuthTokenIdentifier(stringMap.get("id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(stringMap.get("tenant_id"));
    TokenIssuer tokenIssuer = new TokenIssuer(stringMap.get("token_issuer"));
    TokenType tokenType = TokenType.valueOf(stringMap.get("token_type"));
    AccessTokenEntity accessTokenEntity =
        new AccessTokenEntity(decrypt(stringMap.get("encrypted_access_token"), aesCipher));

    User user;
    if (Objects.nonNull(stringMap.get("user_payload"))
        && !stringMap.get("user_payload").isEmpty()) {
      user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    } else {
      user = new User();
    }
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);
    RequestedClientId requestedClientId = new RequestedClientId(stringMap.get("client_id"));
    Client client = jsonConverter.read(stringMap.get("client_payload"), Client.class);
    GrantType grantType = GrantType.of(stringMap.get("grant_type"));
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = new CustomProperties();
    GrantIdTokenClaims idTokenClaims = new GrantIdTokenClaims(stringMap.get("id_token_claims"));
    GrantUserinfoClaims userinfoClaims = new GrantUserinfoClaims(stringMap.get("userinfo_claims"));

    AuthorizationDetails authorizationDetails =
        AuthorizationDetails.fromString(stringMap.get("authorization_details"));
    ConsentClaims consentClaims = convertConsentClaims(stringMap.get("consent_claims"));

    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            tenantIdentifier,
            user,
            authentication,
            requestedClientId,
            client,
            grantType,
            scopes,
            idTokenClaims,
            userinfoClaims,
            customProperties,
            authorizationDetails,
            consentClaims);

    ClientCertificationThumbprint thumbprint =
        new ClientCertificationThumbprint(stringMap.get("client_certification_thumbprint"));
    ExpiresIn expiresIn = new ExpiresIn(stringMap.get("expires_in"));
    ExpiresAt accessTokenExpiresAt = new ExpiresAt(stringMap.get("access_token_expires_at"));
    CreatedAt accessTokenCreatedAt = new CreatedAt(stringMap.get("access_token_created_at"));

    OAuthTokenBuilder oAuthTokenBuilder = new OAuthTokenBuilder(id);

    AccessToken accessToken =
        new AccessToken(
            tenantIdentifier,
            tokenIssuer,
            tokenType,
            accessTokenEntity,
            authorizationGrant,
            thumbprint,
            accessTokenCreatedAt,
            expiresIn,
            accessTokenExpiresAt);
    if (Objects.nonNull(stringMap.get("encrypted_refresh_token"))
        && !stringMap.get("encrypted_refresh_token").equals("{}")) {
      RefreshTokenEntity refreshTokenEntity =
          new RefreshTokenEntity(decrypt(stringMap.get("encrypted_refresh_token"), aesCipher));
      ExpiresAt refreshTokenExpiresAt = new ExpiresAt(stringMap.get("refresh_token_expires_at"));
      CreatedAt refreshTokenCreatedAt = new CreatedAt(stringMap.get("refresh_token_created_at"));
      RefreshToken refreshToken =
          new RefreshToken(refreshTokenEntity, refreshTokenCreatedAt, refreshTokenExpiresAt);
      oAuthTokenBuilder.add(refreshToken);
    }

    IdToken idToken = new IdToken(stringMap.get("id_token"));
    CNonce cNonce = new CNonce(stringMap.get("c_nonce"));
    CNonceExpiresIn cNonceExpiresIn = new CNonceExpiresIn(stringMap.get("c_nonce_expires_in"));

    return oAuthTokenBuilder.add(accessToken).add(idToken).add(cNonce).add(cNonceExpiresIn).build();
  }

  static String toJson(Object obj) {
    return jsonConverter.write(obj);
  }

  private static String decrypt(String encryptedData, AesCipher aesCipher) {
    if (encryptedData == null || encryptedData.isEmpty()) {
      return "";
    }

    EncryptedData data = jsonConverter.read(encryptedData, EncryptedData.class);
    return aesCipher.decrypt(data);
  }

  private static ConsentClaims convertConsentClaims(String value) {
    if (value == null || value.isEmpty()) {
      return new ConsentClaims();
    }
    try {
      Map read = jsonConverter.read(value, Map.class);
      return new ConsentClaims(read);
    } catch (Exception exception) {
      return new ConsentClaims();
    }
  }
}
