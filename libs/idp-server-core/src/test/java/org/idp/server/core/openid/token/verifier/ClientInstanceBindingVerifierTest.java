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

package org.idp.server.core.openid.token.verifier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceThumbprint;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/**
 * What a token issued to a registered Client Instance is bound to: the instance (not only its key)
 * for a refresh, and the user of the instance for any grant that yields a user's token.
 */
class ClientInstanceBindingVerifierTest {

  static final String CLIENT_ID = "mobile-app";
  static final Map<String, Object> INSTANCE_KEY =
      Map.of(
          "kty", "EC",
          "crv", "P-256",
          "x", "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
          "y", "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0");

  static final String USER_A = "11111111-1111-1111-1111-111111111111";
  static final String USER_B = "22222222-2222-2222-2222-222222222222";

  ClientInstance instance(String id, String userId) {
    return new ClientInstance(
        id,
        "tenant",
        CLIENT_ID,
        INSTANCE_KEY,
        "active",
        Map.of(),
        null,
        userId,
        null,
        null,
        null,
        null);
  }

  AuthorizationGrant grantOf(String userSub) {
    AuthorizationGrantBuilder builder =
        new AuthorizationGrantBuilder(
            new TenantIdentifier("33333333-3333-3333-3333-333333333333"),
            new RequestedClientId(CLIENT_ID),
            GrantType.authorization_code,
            new Scopes("openid"));
    if (userSub != null) {
      builder.add(new User().setSub(userSub));
    }
    return builder.build();
  }

  ClientCredentials credentialsOf(ClientInstance instance) throws Exception {
    JsonWebKey key = JwkParser.parse(JsonConverter.snakeCaseInstance().write(INSTANCE_KEY));
    return new ClientCredentials(
        new RequestedClientId(CLIENT_ID),
        ClientAuthenticationType.attest_jwt_client_auth,
        new ClientSecret(),
        new ClientAuthenticationPublicKey(key),
        null,
        new ClientCertification(),
        instance);
  }

  AccessToken tokenIssuedTo(ClientInstance instance) {
    return new AccessToken(
        null,
        null,
        null,
        null,
        grantOf(USER_A),
        null,
        null,
        instance.instanceKeyThumbprint(),
        instance.identifier(),
        null,
        null,
        null,
        null);
  }

  @Test
  void acceptsTheUsersOwnGrant() {
    new ClientInstanceUserBindingVerifier(grantOf(USER_A), instance("i-1", USER_A)).verify();
  }

  @Test
  void refusesTheGrantOfAnotherUser() {
    TokenBadRequestException exception =
        assertThrows(
            TokenBadRequestException.class,
            () ->
                new ClientInstanceUserBindingVerifier(grantOf(USER_B), instance("i-1", USER_A))
                    .verify());
    assertEquals("invalid_grant", exception.error().value());
  }

  @Test
  void passesAGrantWithoutUser() {
    // client_credentials: nothing to compare.
    new ClientInstanceUserBindingVerifier(grantOf(null), instance("i-1", USER_A)).verify();
  }

  @Test
  void passesAnInstanceWithoutUser() {
    // Registered through the management API: bound to no user.
    new ClientInstanceUserBindingVerifier(grantOf(USER_B), instance("i-1", null)).verify();
  }

  @Test
  void refreshesFromTheInstanceTheTokenWasIssuedTo() throws Exception {
    ClientInstance issuedTo = instance("i-1", USER_A);

    new RefreshTokenClientInstanceBindingVerifier(tokenIssuedTo(issuedTo), credentialsOf(issuedTo))
        .verify();
  }

  @Test
  void refusesARefreshFromAnotherRegistrationOfTheSameKey() throws Exception {
    // The instance was deleted and the key registered again: same thumbprint, new instance.
    ClientInstance issuedTo = instance("i-1", USER_A);
    ClientInstance reRegistered = instance("i-2", USER_A);
    assertEquals(issuedTo.instanceKeyThumbprint(), reRegistered.instanceKeyThumbprint());

    TokenBadRequestException exception =
        assertThrows(
            TokenBadRequestException.class,
            () ->
                new RefreshTokenClientInstanceBindingVerifier(
                        tokenIssuedTo(issuedTo), credentialsOf(reRegistered))
                    .verify());
    assertEquals("invalid_grant", exception.error().value());
  }

  @Test
  void aTokenWithoutInstanceIsBoundByTheKeyAlone() throws Exception {
    // Issued through a Client Attester (attester_jwks / x5c): no instance is registered.
    ClientInstance any = instance("i-1", USER_A);
    AccessToken token =
        new AccessToken(
            null,
            null,
            null,
            null,
            grantOf(USER_A),
            null,
            null,
            any.instanceKeyThumbprint(),
            new ClientInstanceIdentifier(),
            null,
            null,
            null,
            null);
    ClientCredentials attesterCredentials = credentialsOf(new ClientInstance());

    new RefreshTokenClientInstanceBindingVerifier(token, attesterCredentials).verify();
    assertInstanceOf(ClientInstanceThumbprint.class, token.clientInstanceThumbprint());
  }
}
