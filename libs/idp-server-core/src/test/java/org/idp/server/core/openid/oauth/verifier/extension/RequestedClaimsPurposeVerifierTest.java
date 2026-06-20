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

package org.idp.server.core.openid.oauth.verifier.extension;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * OIDC Core §5.5.1 {@code purpose} length validation (3–300 chars → else {@code invalid_request}),
 * covering standard {@code id_token}/{@code userinfo} claims and purposes nested inside a {@code
 * verified_claims} request.
 */
class RequestedClaimsPurposeVerifierTest {

  private final RequestedClaimsPurposeVerifier verifier = new RequestedClaimsPurposeVerifier();

  private static OAuthRequestContext context(String claimsJson) {
    RequestedClaimsPayload payload =
        JsonConverter.snakeCaseInstance().read(claimsJson, RequestedClaimsPayload.class);
    AuthorizationRequest request = new AuthorizationRequestBuilder().add(payload).build();
    return new OAuthRequestContext(null, null, null, null, request, null, null);
  }

  private static String repeat(int length) {
    return "a".repeat(length);
  }

  @Test
  void acceptsPurposeWithinRange() {
    assertDoesNotThrow(
        () ->
            verifier.verify(
                context("{\"id_token\":{\"given_name\":{\"purpose\":\"to verify identity\"}}}")));
  }

  @Test
  void acceptsPurposeAtBoundaries() {
    assertDoesNotThrow(
        () -> verifier.verify(context("{\"id_token\":{\"given_name\":{\"purpose\":\"abc\"}}}")));
    assertDoesNotThrow(
        () ->
            verifier.verify(
                context("{\"id_token\":{\"given_name\":{\"purpose\":\"" + repeat(300) + "\"}}}")));
  }

  @Test
  void rejectsPurposeShorterThan3Chars() {
    OAuthRedirectableBadRequestException exception =
        assertThrows(
            OAuthRedirectableBadRequestException.class,
            () -> verifier.verify(context("{\"id_token\":{\"given_name\":{\"purpose\":\"ab\"}}}")));
    assertEquals("invalid_request", exception.error().value());
  }

  @Test
  void rejectsPurposeLongerThan300Chars() {
    assertThrows(
        OAuthRedirectableBadRequestException.class,
        () ->
            verifier.verify(
                context("{\"userinfo\":{\"email\":{\"purpose\":\"" + repeat(301) + "\"}}}")));
  }

  @Test
  void ignoresClaimsWithoutPurpose() {
    assertDoesNotThrow(
        () -> verifier.verify(context("{\"id_token\":{\"given_name\":null,\"email\":{}}}")));
  }

  @Test
  void rejectsTooShortPurposeNestedInVerifiedClaims() {
    assertThrows(
        OAuthRedirectableBadRequestException.class,
        () ->
            verifier.verify(
                context(
                    "{\"id_token\":{\"verified_claims\":{\"verification\":{\"trust_framework\":null},"
                        + "\"claims\":{\"given_name\":{\"purpose\":\"ab\"}}}}}")));
  }

  @Test
  void acceptsHtmlSpecialCharactersWhenLengthIsValid() {
    // Escaping is a display concern (view-data), not request validation: a valid-length purpose
    // containing HTML metacharacters passes verification.
    assertDoesNotThrow(
        () ->
            verifier.verify(
                context("{\"id_token\":{\"given_name\":{\"purpose\":\"<b>name</b>\"}}}")));
  }
}
