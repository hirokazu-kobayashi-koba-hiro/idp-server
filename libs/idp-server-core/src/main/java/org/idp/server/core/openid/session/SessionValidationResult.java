/*
 * Copyright 2026 Hirokazu Kobayashi
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

package org.idp.server.core.openid.session;

import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * SessionValidationResult
 *
 * <p>Represents the result of session validation for authorize-with-session flow. Encapsulates
 * validation outcome, error details, and security event type for audit logging.
 */
public class SessionValidationResult {

  private final boolean valid;
  private final String errorCode;
  private final String errorDescription;
  private final DefaultSecurityEventType eventType;

  private SessionValidationResult(
      boolean valid,
      String errorCode,
      String errorDescription,
      DefaultSecurityEventType eventType) {
    this.valid = valid;
    this.errorCode = errorCode;
    this.errorDescription = errorDescription;
    this.eventType = eventType;
  }

  public static SessionValidationResult success() {
    return new SessionValidationResult(true, null, null, null);
  }

  public static SessionValidationResult sessionNotFound() {
    return new SessionValidationResult(
        false,
        "invalid_request",
        "session not found",
        DefaultSecurityEventType.oauth_authorize_with_session_expired);
  }

  public static SessionValidationResult sessionExpired() {
    return new SessionValidationResult(
        false,
        "invalid_request",
        "session expired",
        DefaultSecurityEventType.oauth_authorize_with_session_expired);
  }

  public static SessionValidationResult acrMismatch() {
    return new SessionValidationResult(
        false,
        "invalid_request",
        "session acr does not satisfy requested acr_values",
        DefaultSecurityEventType.oauth_authorize_with_session_acr_mismatch);
  }

  public static SessionValidationResult policyMismatch() {
    return new SessionValidationResult(
        false,
        "invalid_request",
        "session does not satisfy authentication policy",
        DefaultSecurityEventType.oauth_authorize_with_session_policy_mismatch);
  }

  public boolean isValid() {
    return valid;
  }

  public boolean isInvalid() {
    return !valid;
  }

  public String errorCode() {
    return errorCode;
  }

  public String errorDescription() {
    return errorDescription;
  }

  public DefaultSecurityEventType eventType() {
    return eventType;
  }
}
