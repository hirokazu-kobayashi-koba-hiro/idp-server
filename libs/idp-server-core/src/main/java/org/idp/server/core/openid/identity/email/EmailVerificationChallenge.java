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

package org.idp.server.core.openid.identity.email;

import java.time.LocalDateTime;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.date.SystemDateTime;

/**
 * A one-time code issued for a self-service email verification or change (Issue #1416).
 *
 * <p>The row it maps to is owned by exactly one user: {@code userIdentifier} is part of the lookup
 * predicate, so a challenge belonging to someone else is not found rather than found-and-rejected.
 * That removes the class of bug where an ownership check exists on one entry point and is missing
 * on another.
 */
public class EmailVerificationChallenge {

  EmailVerificationChallengeIdentifier identifier;
  UserIdentifier userIdentifier;
  EmailVerificationOperation operation;
  String targetEmail;
  String verificationCode;
  int attempts;
  LocalDateTime expiresAt;

  public EmailVerificationChallenge() {}

  public EmailVerificationChallenge(
      EmailVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier,
      EmailVerificationOperation operation,
      String targetEmail,
      String verificationCode,
      int attempts,
      LocalDateTime expiresAt) {
    this.identifier = identifier;
    this.userIdentifier = userIdentifier;
    this.operation = operation;
    this.targetEmail = targetEmail;
    this.verificationCode = verificationCode;
    this.attempts = attempts;
    this.expiresAt = expiresAt;
  }

  public static EmailVerificationChallenge create(
      UserIdentifier userIdentifier,
      EmailVerificationOperation operation,
      String targetEmail,
      String verificationCode,
      int expireSeconds) {
    return new EmailVerificationChallenge(
        new EmailVerificationChallengeIdentifier(java.util.UUID.randomUUID().toString()),
        userIdentifier,
        operation,
        targetEmail,
        verificationCode,
        0,
        SystemDateTime.now().plusSeconds(expireSeconds));
  }

  public EmailVerificationChallengeIdentifier identifier() {
    return identifier;
  }

  public UserIdentifier userIdentifier() {
    return userIdentifier;
  }

  public EmailVerificationOperation operation() {
    return operation;
  }

  public String targetEmail() {
    return targetEmail;
  }

  public int attempts() {
    return attempts;
  }

  /** Exposed for persistence only; comparison goes through {@link #matches(String)}. */
  public String verificationCodeValue() {
    return verificationCode;
  }

  /** Returns a copy with one more failed attempt recorded. */
  public EmailVerificationChallenge countUpAttempts() {
    return new EmailVerificationChallenge(
        identifier,
        userIdentifier,
        operation,
        targetEmail,
        verificationCode,
        attempts + 1,
        expiresAt);
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }

  public boolean isExpired() {
    return SystemDateTime.now().isAfter(expiresAt);
  }

  /**
   * Constant-time-ish comparison is unnecessary here: the code is single-use and attempt-capped.
   */
  public boolean matches(String candidate) {
    return verificationCode != null && verificationCode.equals(candidate);
  }

  public boolean exceededRetryLimit(int retryCountLimitation) {
    return attempts >= retryCountLimitation;
  }
}
