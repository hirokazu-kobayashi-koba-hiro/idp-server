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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Denies an authentication interaction that proved a credential for a user whose account is not
 * active (LOCKED / DISABLED / SUSPENDED / DEACTIVATED / DELETED_PENDING / DELETED / UNREGISTERED).
 *
 * <p><b>Issue #1377:</b> authentication interactors resolve the user from the submitted credential
 * but never checked {@link User#isActive()}, so a non-active user with valid credentials could
 * complete an authentication interaction. The downstream status gates are uneven across flows,
 * which is why enforcing here matters:
 *
 * <ul>
 *   <li><b>Authorization code:</b> {@code /authorize} does re-check status, but only after the
 *       interaction has already established an OP session / SSO cookie. Rejecting here prevents
 *       that session from being created for a non-active user.
 *   <li><b>CIBA:</b> only the backchannel <i>request</i> checks status; the grant/token path does
 *       not. A user locked during the pending window would otherwise still be issued tokens, so
 *       this is the only post-request gate.
 *   <li><b>User operation:</b> there is no other status gate, so this is the sole enforcement
 *       point.
 * </ul>
 *
 * <p>This mirrors the token-layer {@code ResourceOwnerPasswordGrantVerifier} / {@code
 * RefreshTokenUserVerifier} status checks, applied at the interaction boundary. Unlike those
 * void+throw verifiers it returns a converted result instead of throwing: the interaction layer is
 * result-based and the caller publishes a security event from {@code result.eventType()}.
 * Converting a successful result into a {@code FORBIDDEN} one keeps that path intact — it emits
 * {@link DefaultSecurityEventType#user_status_inactive} for monitoring and stops the transaction
 * from being marked successful (no session, no authorize, no token).
 *
 * <p>Only {@link OperationType#AUTHENTICATION} results that established a user are examined.
 * Registration and JIT paths always assign an active status ({@code INITIALIZED} / {@code
 * IDENTITY_VERIFICATION_REQUIRED}), so a fresh sign-up is never blocked; challenge / deny /
 * de-registration results either report a non-authentication operation type or carry no established
 * user, and pass through unchanged.
 */
public class AuthenticationUserStatusGuard {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationUserStatusGuard.class);

  private AuthenticationUserStatusGuard() {}

  public static AuthenticationInteractionRequestResult denyIfInactive(
      AuthenticationInteractionRequestResult result) {

    if (!establishesUser(result) || result.user().isActive()) {
      return result;
    }

    User user = result.user();
    log.warn(
        "Authentication interaction succeeded but the user is not active; denying access. sub={}, status={}, method={}",
        user.sub(),
        user.status().name(),
        result.method());

    Map<String, Object> response = new HashMap<>();
    response.put("error", "access_denied");
    response.put("error_description", "The user is not active.");

    return AuthenticationInteractionRequestResult.error(
        403,
        response,
        result.type(),
        result.operationType(),
        result.method(),
        user,
        DefaultSecurityEventType.user_status_inactive);
  }

  private static boolean establishesUser(AuthenticationInteractionRequestResult result) {
    return result.isSuccess() && result.hasUser() && result.operationType().isAuthentication();
  }
}
