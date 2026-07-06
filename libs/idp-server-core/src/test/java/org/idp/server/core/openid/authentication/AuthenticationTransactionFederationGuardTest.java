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

import java.util.UUID;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.federation.FederationInteractionResult;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.core.openid.federation.sso.oidc.OidcSsoSession;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.exception.BadRequestException;
import org.junit.jupiter.api.Test;

/**
 * The federation merge path must apply the same identifier-switching guard as the standard
 * interaction path: once a user is established in the transaction, a federation step returning a
 * different user must be rejected, not silently rebind the transaction.
 */
class AuthenticationTransactionFederationGuardTest {

  private User userWithSub(String sub) {
    return new User().setSub(sub);
  }

  private AuthenticationTransaction transactionWith(User user) {
    AuthenticationRequest request =
        new AuthenticationRequest(null, null, null, null, null, user, null, null, null, null);
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier(UUID.randomUUID().toString()),
        new AuthorizationIdentifier(UUID.randomUUID().toString()),
        request,
        new AuthenticationPolicy(),
        new AuthenticationInteractionResults(),
        new AuthenticationTransactionAttributes());
  }

  private FederationInteractionResult federationSuccess(User user) {
    OidcSsoSession session =
        new OidcSsoSession(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    return FederationInteractionResult.success(
        new FederationType("oidc"), new SsoProvider("google"), session, user);
  }

  @Test
  void rejectsFederationUserSwitchWhenUserAlreadyEstablished() {
    AuthenticationTransaction transaction = transactionWith(userWithSub("user-a"));

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> transaction.updateWith(federationSuccess(userWithSub("user-b"))));
    assertEquals("User is not the same as the request", exception.getMessage());
  }

  @Test
  void bindsToEstablishedUserWhenFederationReturnsSameSub() {
    AuthenticationTransaction transaction = transactionWith(userWithSub("user-a"));

    AuthenticationTransaction updated =
        transaction.updateWith(federationSuccess(userWithSub("user-a")));

    assertEquals("user-a", updated.user().sub());
  }

  @Test
  void establishesUserWhenNoneYet() {
    AuthenticationTransaction transaction = transactionWith(new User());

    AuthenticationTransaction updated =
        transaction.updateWith(federationSuccess(userWithSub("user-b")));

    assertEquals("user-b", updated.user().sub());
  }
}
