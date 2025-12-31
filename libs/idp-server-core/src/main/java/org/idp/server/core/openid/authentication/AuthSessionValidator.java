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

import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * AuthSessionValidator
 *
 * <p>Validates that the AUTH_SESSION cookie from the browser matches the authSessionId stored in
 * the AuthenticationTransaction. This prevents authorization flow hijacking attacks where an
 * attacker could steal an authorization request ID and complete the flow with another user's
 * credentials.
 *
 * <p><b>Attack Prevention:</b>
 *
 * <pre>
 * Without this validation:
 * 1. Attacker starts authorization request → gets id=abc123
 * 2. Attacker sends URL with id=abc123 to victim
 * 3. Victim authenticates on that page (enters victim's credentials)
 * 4. Attacker completes authorization with id=abc123 → logs in as victim
 *
 * With this validation:
 * 1. Attacker starts authorization request → gets id=abc123, cookie=xyz789
 * 2. Attacker sends URL with id=abc123 to victim
 * 3. Victim's browser has different cookie or no cookie
 * 4. Validation fails → attack prevented
 * </pre>
 */
public class AuthSessionValidator {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(AuthSessionValidator.class);

  private AuthSessionValidator() {}

  /**
   * Validates that the cookie authSessionId matches the transaction's authSessionId.
   *
   * <p>This validation is skipped for:
   *
   * <ul>
   *   <li>Transactions without authSessionId (backwards compatibility)
   *   <li>Device-based authentication (CIBA, push notification, etc.)
   * </ul>
   *
   * @param transaction the authentication transaction containing the expected authSessionId
   * @param cookieAuthSessionId the authSessionId from the AUTH_SESSION cookie
   * @throws UnauthorizedException if validation fails (session mismatch or missing)
   */
  public static void validate(
      AuthenticationTransaction transaction, AuthSessionId cookieAuthSessionId) {

    // If transaction has no authSessionId, skip validation (backwards compatibility)
    if (!transaction.hasAuthSessionId()) {
      log.debug("AUTH_SESSION validation skipped: transaction has no authSessionId");
      return;
    }

    // Skip validation for device-based authentication (CIBA, push notification, etc.)
    // These flows use separate authentication mechanisms (device tokens, push confirmation)
    if (transaction.hasAuthenticationDevice()) {
      log.debug("AUTH_SESSION validation skipped: device-based authentication");
      return;
    }

    AuthSessionId expectedAuthSessionId = transaction.authSessionId();

    // Cookie must be present
    if (cookieAuthSessionId == null || !cookieAuthSessionId.exists()) {
      log.warn(
          "AUTH_SESSION validation failed: cookie missing (expected={})",
          maskSessionId(expectedAuthSessionId.value()));
      throw new UnauthorizedException(
          "auth_session_mismatch: Missing AUTH_SESSION cookie. Please restart the authorization flow.");
    }

    // Cookie must match transaction
    if (!expectedAuthSessionId.matches(cookieAuthSessionId)) {
      log.warn(
          "AUTH_SESSION validation failed: mismatch (expected={}, actual={})",
          maskSessionId(expectedAuthSessionId.value()),
          maskSessionId(cookieAuthSessionId.value()));
      throw new UnauthorizedException(
          "auth_session_mismatch: AUTH_SESSION cookie does not match. This authorization request may have been initiated by a different browser session.");
    }

    log.debug(
        "AUTH_SESSION validation passed: id={}", maskSessionId(expectedAuthSessionId.value()));
  }

  /**
   * Creates validation result without throwing exception.
   *
   * @param transaction the authentication transaction
   * @param cookieAuthSessionId the authSessionId from cookie
   * @return validation result
   */
  public static AuthSessionValidationResult validateSafely(
      AuthenticationTransaction transaction, AuthSessionId cookieAuthSessionId) {

    if (!transaction.hasAuthSessionId()) {
      return AuthSessionValidationResult.skipped();
    }

    // Skip validation for device-based authentication
    if (transaction.hasAuthenticationDevice()) {
      return AuthSessionValidationResult.skipped();
    }

    AuthSessionId expectedAuthSessionId = transaction.authSessionId();

    if (cookieAuthSessionId == null || !cookieAuthSessionId.exists()) {
      return AuthSessionValidationResult.failed("Missing AUTH_SESSION cookie");
    }

    if (!expectedAuthSessionId.matches(cookieAuthSessionId)) {
      return AuthSessionValidationResult.failed("AUTH_SESSION cookie mismatch");
    }

    return AuthSessionValidationResult.success();
  }

  /**
   * Masks session ID for secure logging (shows first 8 and last 4 characters).
   *
   * @param sessionId the session ID to mask
   * @return masked session ID
   */
  private static String maskSessionId(String sessionId) {
    if (sessionId == null || sessionId.length() <= 12) {
      return "***";
    }
    return sessionId.substring(0, 8) + "..." + sessionId.substring(sessionId.length() - 4);
  }

  /** Result of AUTH_SESSION validation. */
  public static class AuthSessionValidationResult {
    private final boolean valid;
    private final boolean skipped;
    private final String errorMessage;

    private AuthSessionValidationResult(boolean valid, boolean skipped, String errorMessage) {
      this.valid = valid;
      this.skipped = skipped;
      this.errorMessage = errorMessage;
    }

    public static AuthSessionValidationResult success() {
      return new AuthSessionValidationResult(true, false, null);
    }

    public static AuthSessionValidationResult skipped() {
      return new AuthSessionValidationResult(true, true, null);
    }

    public static AuthSessionValidationResult failed(String errorMessage) {
      return new AuthSessionValidationResult(false, false, errorMessage);
    }

    public boolean isValid() {
      return valid;
    }

    public boolean isSkipped() {
      return skipped;
    }

    public String errorMessage() {
      return errorMessage;
    }
  }
}
