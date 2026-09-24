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

package org.idp.server.core.openid.clientinstance.registration.verifier;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallenge;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationException;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRequestHash;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JoseHandler;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JoseType;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.jose.JwtClockSkewException;
import org.idp.server.platform.jose.JwtClockSkewValidator;

/**
 * Authenticates a registration by the ID token presented with it.
 *
 * <p>The ID token says who logged in; the platform evidence says which device and key. Neither
 * alone ties the other: a leaked ID token could otherwise be replayed next to an attacker's own
 * genuine device. The tie is the {@code nonce}, which the client sets to the {@link
 * ClientInstanceRequestHash} of the challenge and the key it is about to register, so an ID token
 * only authenticates the registration of that key.
 *
 * <ol>
 *   <li>The ID token is a JWS this Authorization Server signed (encrypted ID tokens are addressed
 *       to the client and cannot be read here)
 *   <li>{@code iss} is this Authorization Server, {@code exp} has not passed
 *   <li>{@code aud} is the client being registered, or a client it lists in {@code
 *       client_instance_registration_clients}
 *   <li>{@code nonce} equals the request hash of the ticket's challenge and the presented key
 *   <li>{@code iat} is not before the challenge was issued: the login happened for this
 *       registration, not before it
 *   <li>{@code sub} is present
 * </ol>
 *
 * <p>Resolving {@code sub} to a user is left to the caller, which also decides whether that user
 * may register.
 */
public class ClientInstanceRegistrationIdTokenVerifier {

  JoseHandler joseHandler = new JoseHandler();

  public JsonWebTokenClaims verify(
      AuthorizationServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration,
      ClientInstanceRegistrationChallenge challenge,
      Map<String, Object> instanceKey,
      String idToken) {

    JsonWebTokenClaims claims = verifySignature(serverConfiguration, idToken);

    throwExceptionIfIssuerDoesNotMatch(serverConfiguration, claims);
    throwExceptionIfExpired(claims);
    throwExceptionIfAudienceIsNotAllowed(clientConfiguration, challenge, claims);
    throwExceptionIfNonceDoesNotBindTheKey(challenge, instanceKey, claims);
    throwExceptionIfIssuedBeforeChallenge(challenge, claims);
    throwExceptionIfSubjectIsMissing(claims);

    return claims;
  }

  private JsonWebTokenClaims verifySignature(
      AuthorizationServerConfiguration serverConfiguration, String idToken) {
    if (idToken == null || idToken.isEmpty()) {
      throw new ClientInstanceRegistrationException("id_token is required");
    }

    try {
      if (JoseType.parse(idToken) != JoseType.signature) {
        throw new ClientInstanceRegistrationException("id_token must be a signed JWT");
      }

      String serverJwks = serverConfiguration.jwks();
      JoseContext joseContext = joseHandler.handle(idToken, serverJwks, serverJwks, "");
      joseContext.verifySignature();

      return joseContext.claims();
    } catch (JoseInvalidException e) {
      throw new ClientInstanceRegistrationException("id_token is invalid: " + e.getMessage(), e);
    }
  }

  private void throwExceptionIfIssuerDoesNotMatch(
      AuthorizationServerConfiguration serverConfiguration, JsonWebTokenClaims claims) {
    if (!claims.hasIss() || !claims.getIss().equals(serverConfiguration.issuer())) {
      throw new ClientInstanceRegistrationException(
          "id_token was not issued by this authorization server");
    }
  }

  private void throwExceptionIfExpired(JsonWebTokenClaims claims) {
    if (!claims.hasExp() || claims.getExp() == null) {
      throw new ClientInstanceRegistrationException("id_token has no exp claim");
    }
    if (claims.getExp().getTime() <= SystemDateTime.currentEpochMilliSecond()) {
      throw new ClientInstanceRegistrationException("id_token has expired");
    }
  }

  /**
   * The client's own ID tokens are always accepted. Those of another client only when this client
   * names it: without the allow list, any client of the tenant — a third party application the user
   * merely logged into — could authenticate the registration of this client's instances.
   */
  private void throwExceptionIfAudienceIsNotAllowed(
      ClientConfiguration clientConfiguration,
      ClientInstanceRegistrationChallenge challenge,
      JsonWebTokenClaims claims) {

    List<String> audience = claims.hasAud() ? claims.getAud() : List.of();
    List<String> registrationClients = clientConfiguration.clientInstanceRegistrationClients();

    boolean allowed =
        audience.contains(challenge.clientId())
            || audience.stream().anyMatch(registrationClients::contains);

    if (!allowed) {
      throw new ClientInstanceRegistrationException(
          "id_token audience is neither the client nor one of its registration clients");
    }
  }

  private void throwExceptionIfNonceDoesNotBindTheKey(
      ClientInstanceRegistrationChallenge challenge,
      Map<String, Object> instanceKey,
      JsonWebTokenClaims claims) {

    ClientInstanceRequestHash expected =
        ClientInstanceRequestHash.derive(challenge.challenge(), instanceKey);

    if (!expected.matches(claims.getValue("nonce"))) {
      throw new ClientInstanceRegistrationException(
          "id_token nonce does not bind the challenge to the instance key");
    }
  }

  private void throwExceptionIfIssuedBeforeChallenge(
      ClientInstanceRegistrationChallenge challenge, JsonWebTokenClaims claims) {
    if (!claims.hasIat() || claims.getIat() == null) {
      throw new ClientInstanceRegistrationException("id_token has no iat claim");
    }

    try {
      JwtClockSkewValidator.validateIatNbf(claims);
    } catch (JwtClockSkewException e) {
      throw new ClientInstanceRegistrationException("id_token " + e.getMessage(), e);
    }

    long skewMillis = JwtClockSkewValidator.MAX_CLOCK_SKEW_SECONDS * 1000L;
    long challengeIssuedAt = SystemDateTime.toEpochMilli(challenge.createdAt());
    if (claims.getIat().getTime() < challengeIssuedAt - skewMillis) {
      throw new ClientInstanceRegistrationException(
          "id_token was issued before the registration challenge");
    }
  }

  private void throwExceptionIfSubjectIsMissing(JsonWebTokenClaims claims) {
    if (!claims.hasSub() || claims.getSub() == null || claims.getSub().isEmpty()) {
      throw new ClientInstanceRegistrationException("id_token has no sub claim");
    }
  }
}
