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

package org.idp.server.core.openid.oauth.clientauthenticator;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientSecretBasicUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.UseAttestationChallengeException;
import org.idp.server.core.openid.oauth.handler.OAuthRequestErrorHandler;
import org.idp.server.core.openid.oauth.io.OAuthPushedRequestResponse;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.handler.token.TokenRequestErrorHandler;
import org.idp.server.core.openid.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.openid.token.handler.tokenintrospection.TokenIntrospectionErrorHandler;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.junit.jupiter.api.Test;

/**
 * Issue #1891: the response to a failed client authentication.
 *
 * <p>RFC 6749 Section 5.2 has a client that attempted to authenticate via the {@code Authorization}
 * header answered with 401 and a {@code WWW-Authenticate} challenge of the scheme it used. RFC 7662
 * Section 2.3 has the introspection endpoint answer invalid client credentials with 401, and keeps
 * {@code "active": false} for an authorized query. Only {@code use_attestation_challenge} is 400
 * (draft-ietf-oauth-attestation-based-client-auth-11 Section 6.1).
 */
class ClientAuthenticationChallengeTest {

  private static final String ISSUER = "https://idp.example.com/tenant-1";
  private static final RequestedClientId CLIENT_ID = new RequestedClientId("client-1");

  private static ClientSecretBasicUnAuthorizedException basicFailure() {
    return new ClientSecretBasicUnAuthorizedException(
        "client_secret_basic", CLIENT_ID, "client_secret does not match", ISSUER);
  }

  private static ClientUnAuthorizedException failure() {
    return new ClientUnAuthorizedException(
        "client_secret_post", CLIENT_ID, "client_secret does not match");
  }

  @Test
  void basicFailureCarriesABasicChallengeNamingTheIssuer() {
    ClientSecretBasicUnAuthorizedException exception = basicFailure();

    assertEquals("invalid_client", exception.errorCode());
    assertEquals(
        "Basic realm=\"" + ISSUER + "\"",
        exception.responseHeaders().get(ClientSecretBasicUnAuthorizedException.HEADER_NAME));
  }

  @Test
  void tokenEndpointSendsTheChallengeWith401() {
    TokenRequestResponse response = new TokenRequestErrorHandler().handle(basicFailure());

    assertEquals(401, response.statusCode());
    assertEquals(
        "Basic realm=\"" + ISSUER + "\"",
        response.responseHeaders().get(ClientSecretBasicUnAuthorizedException.HEADER_NAME));
  }

  @Test
  void pushedAuthorizationRequestEndpointAnswersInvalidClientWith401() {
    OAuthPushedRequestResponse response =
        new OAuthRequestErrorHandler().handlePushedRequest(failure());

    assertEquals(401, response.statusCode());
    assertTrue(response.responseHeaders().isEmpty());
  }

  @Test
  void pushedAuthorizationRequestEndpointSendsTheChallengeForBasic() {
    OAuthPushedRequestResponse response =
        new OAuthRequestErrorHandler().handlePushedRequest(basicFailure());

    assertEquals(401, response.statusCode());
    assertEquals(
        "Basic realm=\"" + ISSUER + "\"",
        response.responseHeaders().get(ClientSecretBasicUnAuthorizedException.HEADER_NAME));
  }

  @Test
  void pushedAuthorizationRequestEndpointAnswersUseAttestationChallengeWith400() {
    OAuthPushedRequestResponse response =
        new OAuthRequestErrorHandler()
            .handlePushedRequest(
                new UseAttestationChallengeException(
                    "attest_jwt_client_auth", CLIENT_ID, "no challenge", "fresh-challenge"));

    assertEquals(400, response.statusCode());
    assertEquals(
        "fresh-challenge",
        response.responseHeaders().get(UseAttestationChallengeException.CHALLENGE_HEADER_NAME));
  }

  @Test
  void introspectionEndpointAnswersInvalidClientWith401AndNoActive() {
    TokenIntrospectionResponse response = new TokenIntrospectionErrorHandler().handle(failure());

    assertEquals(401, response.statusCode());
    assertEquals("invalid_client", response.response().get("error"));
    assertFalse(response.response().containsKey("active"));
  }
}
