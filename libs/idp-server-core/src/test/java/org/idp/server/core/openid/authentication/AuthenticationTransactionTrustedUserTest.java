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

package org.idp.server.core.openid.authentication;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.junit.jupiter.api.Test;

/**
 * Issue #1862: {@link AuthenticationTransaction#hasTrustedUser()} separates "who is this
 * transaction about" from "who established that". Only a verified factor and a client-authenticated
 * CIBA request may reach an external endpoint as {@code $.user}.
 */
class AuthenticationTransactionTrustedUserTest {

  private static User user() {
    return new User().setSub("victim-sub").setProviderId("idp-server");
  }

  private static AuthenticationTransaction transaction(
      StandardAuthFlow flow, User user, AuthenticationInteractionResults results) {
    AuthenticationRequest request =
        new AuthenticationRequest(
            flow.toAuthFlow(),
            null,
            null,
            new RequestedClientId("client"),
            null,
            user,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(10));
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier("tx"),
        new AuthorizationIdentifier("auth"),
        request,
        new AuthenticationPolicy(),
        results,
        new AuthenticationTransactionAttributes());
  }

  private static AuthenticationInteractionResults results(String operationType, int successCount) {
    return new AuthenticationInteractionResults(
        Map.of(
            "password",
            new AuthenticationInteractionResult(
                operationType, "password", 1, successCount, 0, LocalDateTime.now())));
  }

  @Test
  void aTransactionWithoutAUserHasNoTrustedUser() {
    AuthenticationTransaction transaction =
        transaction(
            StandardAuthFlow.OAUTH, User.notFound(), new AuthenticationInteractionResults());

    assertFalse(transaction.hasTrustedUser());
  }

  @Test
  void aLoginHintUserIsNotTrustedUntilAFactorSucceeds() {
    // The authorization endpoint takes no client authentication, so login_hint lets anyone name
    // anyone. The user is in the transaction, but nothing has been proven.
    AuthenticationTransaction transaction =
        transaction(StandardAuthFlow.OAUTH, user(), new AuthenticationInteractionResults());

    assertTrue(transaction.hasUser());
    assertFalse(transaction.hasTrustedUser());
  }

  @Test
  void aUserBecomesTrustedOnceAnAuthenticationInteractionSucceeds() {
    AuthenticationTransaction transaction =
        transaction(StandardAuthFlow.OAUTH, user(), results("AUTHENTICATION", 1));

    assertTrue(transaction.hasTrustedUser());
  }

  @Test
  void aFailedAuthenticationInteractionDoesNotMakeTheUserTrusted() {
    AuthenticationTransaction transaction =
        transaction(StandardAuthFlow.OAUTH, user(), results("AUTHENTICATION", 0));

    assertFalse(transaction.hasTrustedUser());
  }

  @Test
  void aSucceededChallengeIsNotAnAuthentication() {
    // Sending an OTP proves nothing about who is on the other end.
    AuthenticationTransaction transaction =
        transaction(StandardAuthFlow.OAUTH, user(), results("CHALLENGE", 1));

    assertFalse(transaction.hasTrustedUser());
  }

  @Test
  void aCibaUserIsTrustedFromTheFirstInteraction() {
    // The backchannel request is client-authenticated before the transaction exists, so the client
    // vouches for the identity it named. This is what keeps the documented CIBA behaviour working.
    AuthenticationTransaction transaction =
        transaction(StandardAuthFlow.CIBA, user(), new AuthenticationInteractionResults());

    assertTrue(transaction.hasTrustedUser());
  }
}
