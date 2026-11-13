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

package org.idp.server.account_linking.verifier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenRequest;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceThumbprint;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.dpop.JwkThumbprint;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.OAuthTokenBuilder;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.core.openid.token.tokenintrospection.verifier.DPoPBindingVerifier;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.junit.jupiter.api.Test;

/**
 * A sender-constrained user token is accepted only from its sender, and a bearer token passes.
 *
 * <p>The DPoP proof itself is verified by the shared {@link DPoPBindingVerifier}; what is pinned
 * here is that this endpoint asks for it and answers its failures in its own terms.
 */
class LinkedExternalAccountTokenBindingVerifierTest {

  static final String CLIENT_ID = "mobile-app";
  static final Map<String, Object> INSTANCE_KEY =
      Map.of(
          "kty", "EC",
          "crv", "P-256",
          "x", "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
          "y", "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0");
  static final Map<String, Object> OTHER_INSTANCE_KEY =
      Map.of(
          "kty", "EC",
          "crv", "P-256",
          "x", "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4",
          "y", "4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM");

  LinkedExternalAccountTokenBindingVerifier verifier =
      new LinkedExternalAccountTokenBindingVerifier(new DPoPBindingVerifier(null));

  ClientInstance instance(String id, Map<String, Object> key) {
    return new ClientInstance(
        id, "tenant", CLIENT_ID, key, "active", Map.of(), null, "user", null, null, null, null);
  }

  ClientCredentials attestedWith(Map<String, Object> key, ClientInstance instance)
      throws Exception {
    JsonWebKey jwk = JwkParser.parse(JsonConverter.snakeCaseInstance().write(key));
    return new ClientCredentials(
        new RequestedClientId(CLIENT_ID),
        ClientAuthenticationType.attest_jwt_client_auth,
        new ClientSecret(),
        new ClientAuthenticationPublicKey(jwk),
        null,
        new ClientCertification(),
        instance);
  }

  /** What the authenticator returns for a client that authenticated with its secret. */
  ClientCredentials authenticatedWithSecret() {
    return new ClientCredentials(
        new RequestedClientId(CLIENT_ID),
        ClientAuthenticationType.client_secret_post,
        new ClientSecret("secret"),
        new ClientAuthenticationPublicKey(),
        new ClientAssertionJwt(),
        new ClientCertification());
  }

  OAuthToken token(
      JwkThumbprint jkt,
      ClientInstanceThumbprint instanceThumbprint,
      ClientInstanceIdentifier instanceIdentifier) {
    AccessToken accessToken =
        new AccessToken(
            null,
            null,
            null,
            null,
            null,
            new ClientCertificationThumbprint(),
            jkt,
            instanceThumbprint,
            instanceIdentifier,
            null,
            null,
            null,
            null);
    return new OAuthTokenBuilder(new OAuthTokenIdentifier("token-id")).add(accessToken).build();
  }

  OAuthToken boundTo(ClientInstance instance) {
    return token(null, instance.instanceKeyThumbprint(), instance.identifier());
  }

  LinkedExternalAccountTokenRequest request(Map<String, List<String>> headers) {
    return new LinkedExternalAccountTokenRequest(
        new Tenant(),
        new AccountAlias("account-linking-1"),
        new HttpRequestInputs(
            null,
            Map.of(),
            headers,
            null,
            "POST",
            "https://api.example.com/t/v1/linked-external-accounts/account-linking-1/token"));
  }

  @Test
  void passesABearerToken() {
    verifier.verify(token(null, null, null), request(Map.of()), new ClientCredentials());
  }

  @Test
  void passesATokenPresentedByTheInstanceItWasIssuedTo() throws Exception {
    ClientInstance instance = instance("i-1", INSTANCE_KEY);

    verifier.verify(boundTo(instance), request(Map.of()), attestedWith(INSTANCE_KEY, instance));
  }

  @Test
  void refusesATokenPresentedByAnotherInstanceOfTheSameClient() throws Exception {
    ClientInstance mine = instance("i-1", INSTANCE_KEY);
    ClientInstance other = instance("i-2", OTHER_INSTANCE_KEY);

    LinkedExternalAccountTokenException exception =
        assertThrows(
            LinkedExternalAccountTokenException.class,
            () ->
                verifier.verify(
                    boundTo(mine), request(Map.of()), attestedWith(OTHER_INSTANCE_KEY, other)));
    assertEquals("invalid_token", exception.error());
  }

  @Test
  void refusesATokenOfAnEarlierRegistrationOfTheSameKey() throws Exception {
    ClientInstance earlier = instance("i-1", INSTANCE_KEY);
    ClientInstance registeredAgain = instance("i-2", INSTANCE_KEY);

    LinkedExternalAccountTokenException exception =
        assertThrows(
            LinkedExternalAccountTokenException.class,
            () ->
                verifier.verify(
                    boundTo(earlier),
                    request(Map.of()),
                    attestedWith(INSTANCE_KEY, registeredAgain)));
    assertEquals("invalid_token", exception.error());
  }

  @Test
  void refusesAnInstanceBoundTokenWithoutAnInstanceKey() {
    // e.g. a confidential client of the same client_id authenticated with a secret.
    LinkedExternalAccountTokenException exception =
        assertThrows(
            LinkedExternalAccountTokenException.class,
            () ->
                verifier.verify(
                    boundTo(instance("i-1", INSTANCE_KEY)),
                    request(Map.of()),
                    authenticatedWithSecret()));
    assertEquals("invalid_token", exception.error());
  }

  @Test
  void refusesADPoPBoundTokenWithoutAProof() {
    LinkedExternalAccountTokenException exception =
        assertThrows(
            LinkedExternalAccountTokenException.class,
            () ->
                verifier.verify(
                    token(new JwkThumbprint("jkt"), null, null),
                    request(Map.of()),
                    new ClientCredentials()));
    assertEquals("invalid_token", exception.error());
  }

  @Test
  void refusesTwoDPoPHeadersAsAMalformedRequest() {
    LinkedExternalAccountTokenException exception =
        assertThrows(
            LinkedExternalAccountTokenException.class,
            () ->
                verifier.verify(
                    token(null, null, null),
                    request(Map.of("dpop", List.of("proof-1", "proof-2"))),
                    new ClientCredentials()));
    assertEquals("invalid_request", exception.error());
  }
}
