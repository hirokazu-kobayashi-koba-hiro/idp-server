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

package org.idp.server.core.openid.oauth.clientauthenticator.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.handler.token.TokenRequestErrorHandler;
import org.idp.server.core.openid.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.openid.token.handler.tokenintrospection.TokenIntrospectionErrorHandler;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.openid.token.handler.tokenrevocation.TokenRevocationErrorHandler;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.junit.jupiter.api.Test;

/**
 * draft-ietf-oauth-attestation-based-client-auth-10 Section 7.4: the attestation specific error
 * codes are carried by dedicated subclasses of {@link ClientUnAuthorizedException}, so every error
 * handler resolves the reported code through {@link ClientUnAuthorizedException#errorCode()} rather
 * than hard-coding {@code invalid_client}.
 */
class ClientAuthenticationErrorCodeTest {

  private static final String METHOD = "attest_jwt_client_auth";
  private static final RequestedClientId CLIENT_ID = new RequestedClientId("client-1");

  @Test
  void generalClientAuthenticationFailureKeepsInvalidClient() {
    ClientUnAuthorizedException exception =
        new ClientUnAuthorizedException(METHOD, CLIENT_ID, "no credential presented");

    assertEquals("invalid_client", exception.errorCode());
  }

  @Test
  void attestationVerificationFailureReportsInvalidClientAttestation() {
    InvalidClientAttestationException exception =
        new InvalidClientAttestationException(METHOD, CLIENT_ID, "signature verification failed");

    assertEquals("invalid_client_attestation", exception.errorCode());
    // The 401 mapping is shared with the general failure, so the handlers need no extra branch.
    assertInstanceOf(ClientUnAuthorizedException.class, exception);
  }

  @Test
  void staleAttestationReportsUseFreshAttestation() {
    UseFreshAttestationException exception =
        new UseFreshAttestationException(METHOD, CLIENT_ID, "client attestation jwt is expired");

    assertEquals("use_fresh_attestation", exception.errorCode());
    assertInstanceOf(ClientUnAuthorizedException.class, exception);
  }

  @Test
  void causeIsPreservedOnTheAttestationSubclass() {
    RuntimeException cause = new RuntimeException("boom");
    InvalidClientAttestationException exception =
        new InvalidClientAttestationException(METHOD, CLIENT_ID, "malformed jwt", cause);

    assertSame(cause, exception.getCause());
    assertTrue(exception.hasStructuredData());
  }

  @Test
  void useAttestationChallengeCarriesAFreshChallengeOnTheResponseHeader() {
    // Section 7.4: the error code MUST be accompanied by OAuth-Client-Attestation-Challenge.
    UseAttestationChallengeException exception =
        new UseAttestationChallengeException(METHOD, CLIENT_ID, "no challenge", "fresh-challenge");

    assertEquals("use_attestation_challenge", exception.errorCode());
    assertEquals("fresh-challenge", exception.challenge());
    assertEquals(
        "fresh-challenge",
        exception.responseHeaders().get(UseAttestationChallengeException.CHALLENGE_HEADER_NAME));
  }

  @Test
  void generalFailureCarriesNoExtraResponseHeader() {
    ClientUnAuthorizedException exception =
        new ClientUnAuthorizedException(METHOD, CLIENT_ID, "no credential presented");

    assertTrue(exception.responseHeaders().isEmpty());
  }

  @Test
  void tokenEndpointCopiesTheChallengeHeaderOntoTheResponse() {
    TokenRequestResponse response =
        new TokenRequestErrorHandler()
            .handle(
                new UseAttestationChallengeException(
                    METHOD, CLIENT_ID, "no challenge", "fresh-challenge"));

    assertEquals(401, response.statusCode());
    assertTrue(response.contents().contains("use_attestation_challenge"), response.contents());
    assertEquals(
        "fresh-challenge",
        response.responseHeaders().get(UseAttestationChallengeException.CHALLENGE_HEADER_NAME));
  }

  @Test
  void revocationEndpointCopiesTheChallengeHeaderOntoTheResponse() {
    TokenRevocationResponse response =
        new TokenRevocationErrorHandler()
            .handle(
                new UseAttestationChallengeException(
                    METHOD, CLIENT_ID, "no challenge", "fresh-challenge"));

    assertEquals(
        "fresh-challenge",
        response.responseHeaders().get(UseAttestationChallengeException.CHALLENGE_HEADER_NAME));
  }

  @Test
  void tokenEndpointReportsTheSubclassErrorCode() {
    TokenRequestResponse response =
        new TokenRequestErrorHandler()
            .handle(new UseFreshAttestationException(METHOD, CLIENT_ID, "expired"));

    assertEquals(401, response.statusCode());
    assertTrue(response.contents().contains("use_fresh_attestation"), response.contents());
  }

  @Test
  void revocationEndpointReportsTheSubclassErrorCode() {
    TokenRevocationResponse response =
        new TokenRevocationErrorHandler()
            .handle(new InvalidClientAttestationException(METHOD, CLIENT_ID, "bad signature"));

    assertEquals("invalid_client_attestation", response.response().get("error"));
  }

  @Test
  void introspectionEndpointReportsTheSubclassErrorCode() {
    TokenIntrospectionResponse response =
        new TokenIntrospectionErrorHandler()
            .handle(new InvalidClientAttestationException(METHOD, CLIENT_ID, "bad signature"));

    assertEquals("invalid_client_attestation", response.response().get("error"));
    assertEquals(false, response.response().get("active"));
  }
}
