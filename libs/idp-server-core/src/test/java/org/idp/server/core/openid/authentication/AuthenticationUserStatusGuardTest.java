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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Issue #1377: reject authentication of a non-active user at the interaction boundary. */
class AuthenticationUserStatusGuardTest {

  private User userWithStatus(UserStatus status) {
    return new User().setSub(UUID.randomUUID().toString()).setStatus(status);
  }

  private AuthenticationInteractionRequestResult successAuthentication(User user) {
    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        StandardAuthenticationInteraction.PASSWORD_AUTHENTICATION.toType(),
        OperationType.AUTHENTICATION,
        "password",
        user,
        new HashMap<>(),
        DefaultSecurityEventType.password_success);
  }

  @ParameterizedTest
  @EnumSource(
      value = UserStatus.class,
      names = {
        "LOCKED",
        "DISABLED",
        "SUSPENDED",
        "DEACTIVATED",
        "DELETED_PENDING",
        "DELETED",
        "UNREGISTERED"
      })
  void deniesInactiveUserOnAuthenticationSuccess(UserStatus inactiveStatus) {
    User user = userWithStatus(inactiveStatus);

    AuthenticationInteractionRequestResult result =
        AuthenticationUserStatusGuard.denyIfInactive(successAuthentication(user));

    assertEquals(AuthenticationInteractionStatus.FORBIDDEN, result.status());
    assertEquals(403, result.statusCode());
    assertTrue(result.isError());
    assertFalse(result.isSuccess());
    assertEquals("access_denied", result.response().get("error"));
    assertEquals(
        DefaultSecurityEventType.user_status_inactive.toEventType().value(),
        result.eventType().value());
    // user is preserved for security-event attribution
    assertSame(user, result.user());
    // interaction identity metadata is preserved
    assertEquals("password", result.method());
  }

  @ParameterizedTest
  @EnumSource(
      value = UserStatus.class,
      names = {
        "INITIALIZED",
        "FEDERATED",
        "REGISTERED",
        "IDENTITY_VERIFIED",
        "IDENTITY_VERIFICATION_REQUIRED"
      })
  void passesThroughActiveUser(UserStatus activeStatus) {
    AuthenticationInteractionRequestResult input =
        successAuthentication(userWithStatus(activeStatus));

    AuthenticationInteractionRequestResult result =
        AuthenticationUserStatusGuard.denyIfInactive(input);

    assertSame(input, result);
    assertTrue(result.isSuccess());
  }

  @Test
  void passesThroughNonAuthenticationOperationType() {
    // A challenge step may carry the transaction user, but it does not establish a credential
    // proof,
    // so status must not be enforced here (operationType != AUTHENTICATION).
    User locked = userWithStatus(UserStatus.LOCKED);
    AuthenticationInteractionRequestResult input =
        new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.SUCCESS,
            StandardAuthenticationInteraction.EMAIL_AUTHENTICATION_CHALLENGE.toType(),
            OperationType.CHALLENGE,
            "email",
            locked,
            new HashMap<>(),
            DefaultSecurityEventType.email_verification_request_success);

    AuthenticationInteractionRequestResult result =
        AuthenticationUserStatusGuard.denyIfInactive(input);

    assertSame(input, result);
    assertTrue(result.isSuccess());
  }

  @Test
  void passesThroughWhenNoUserEstablished() {
    // A device confirmation step (e.g. number-matching) succeeds without carrying a user of its
    // own.
    AuthenticationInteractionRequestResult input =
        new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.SUCCESS,
            StandardAuthenticationInteraction.PASSWORD_AUTHENTICATION.toType(),
            OperationType.AUTHENTICATION,
            "password",
            new HashMap<>(),
            DefaultSecurityEventType.password_success);

    AuthenticationInteractionRequestResult result =
        AuthenticationUserStatusGuard.denyIfInactive(input);

    assertSame(input, result);
  }

  @Test
  void passesThroughErrorResult() {
    // An interactor that already failed is left untouched (isSuccess() == false).
    User locked = userWithStatus(UserStatus.LOCKED);
    Map<String, Object> body = new HashMap<>();
    body.put("error", "invalid_request");
    AuthenticationInteractionRequestResult input =
        AuthenticationInteractionRequestResult.clientError(
            body,
            StandardAuthenticationInteraction.PASSWORD_AUTHENTICATION.toType(),
            OperationType.AUTHENTICATION,
            "password",
            locked,
            DefaultSecurityEventType.password_failure);

    AuthenticationInteractionRequestResult result =
        AuthenticationUserStatusGuard.denyIfInactive(input);

    assertSame(input, result);
    assertEquals("invalid_request", result.response().get("error"));
  }
}
