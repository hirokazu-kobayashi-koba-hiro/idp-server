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

package org.idp.server.core.openid.identity.id_token;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.plugin.CustomIndividualClaimsCreators;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.exception.ConfigurationInvalidException;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.State;
import org.idp.server.core.openid.oauth.type.oauth.TokenIssuer;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.*;

public class IdTokenCreator implements IndividualClaimsCreatable, ClaimHashable {

  CustomIndividualClaimsCreators customIndividualClaimsCreators;

  private static final IdTokenCreator INSTANCE = new IdTokenCreator();

  public static IdTokenCreator getInstance() {
    return INSTANCE;
  }

  private IdTokenCreator() {
    this.customIndividualClaimsCreators = new CustomIndividualClaimsCreators();
  }

  public IdToken createIdToken(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    try {

      Map<String, Object> claims =
          createIndividualClaims(
              user,
              authentication,
              customClaims,
              authorizationGrant,
              requestedClaimsPayload,
              authorizationServerConfiguration.tokenIssuer(),
              authorizationServerConfiguration.idTokenDuration(),
              authorizationServerConfiguration.isIdTokenStrictMode());

      Map<String, Object> customIndividualClaims =
          customIndividualClaimsCreators.createCustomIndividualClaims(
              user,
              authentication,
              authorizationGrant,
              customClaims,
              requestedClaimsPayload,
              authorizationServerConfiguration,
              clientConfiguration);
      claims.putAll(customIndividualClaims);

      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              claims,
              Map.of(),
              authorizationServerConfiguration.jwks(),
              authorizationServerConfiguration.idTokenSignedKeyId());

      if (clientConfiguration.hasEncryptedIdTokenMeta()) {

        NestedJsonWebEncryptionCreator nestedJsonWebEncryptionCreator =
            new NestedJsonWebEncryptionCreator(
                jsonWebSignature,
                clientConfiguration.idTokenEncryptedResponseAlg(),
                clientConfiguration.idTokenEncryptedResponseEnc(),
                clientConfiguration.jwks(),
                clientConfiguration.clientSecretValue());
        String jwe = nestedJsonWebEncryptionCreator.create();
        return new IdToken(jwe);
      }

      return new IdToken(jsonWebSignature.serialize());
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }

  private Map<String, Object> createIndividualClaims(
      User user,
      Authentication authentication,
      IdTokenCustomClaims idTokenCustomClaims,
      AuthorizationGrant authorizationGrant,
      RequestedClaimsPayload requestedClaimsPayload,
      TokenIssuer tokenIssuer,
      long idTokenDuration,
      boolean idTokenStrictMode) {

    LocalDateTime now = SystemDateTime.now();
    ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(idTokenDuration));
    HashMap<String, Object> claims = new HashMap<>();
    claims.put("iss", tokenIssuer.value());
    claims.put("aud", authorizationGrant.requestedClientId().value());
    claims.put("exp", expiresAt.toEpochSecondWithUtc());
    claims.put("iat", SystemDateTime.toEpochSecond(now));

    if (idTokenCustomClaims.hasNonce()) {
      claims.put("nonce", idTokenCustomClaims.nonce().value());
    }
    if (idTokenCustomClaims.hasState()) {
      State state = idTokenCustomClaims.state();
      claims.put("s_hash", hash(state.value(), "ES256"));
    }
    if (idTokenCustomClaims.hasAuthorizationCode()) {
      claims.put("c_hash", hash(idTokenCustomClaims.authorizationCode().value(), "ES256"));
    }
    if (idTokenCustomClaims.hasAccessTokenValue()) {
      claims.put("at_hash", hash(idTokenCustomClaims.accessTokenValue().value(), "ES256"));
    }
    if (authentication.hasAuthenticationTime()) {
      claims.put("auth_time", SystemDateTime.toEpochSecond(authentication.time()));
    }
    if (authentication.hasMethod()) {
      claims.put("amr", authentication.methods());
    }
    if (authentication.hasAcrValues()) {
      claims.put("acr", authentication.acr());
    }

    Map<String, Object> individualClaims =
        createIndividualClaims(
            user,
            authorizationGrant.idTokenClaims(),
            idTokenStrictMode,
            requestedClaimsPayload.idToken());
    claims.putAll(individualClaims);
    return claims;
  }
}
