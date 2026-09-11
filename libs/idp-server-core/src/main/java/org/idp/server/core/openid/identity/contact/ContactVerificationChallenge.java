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

package org.idp.server.core.openid.identity.contact;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.date.SystemDateTime;

/**
 * A one-time code issued for a self-service contact verification or change (Issue #1416).
 *
 * <p>The row it maps to is owned by exactly one user: {@code userIdentifier} is part of the lookup
 * predicate, so a challenge belonging to someone else is not found rather than found-and-rejected.
 * That removes the class of bug where an ownership check exists on one entry point and is missing
 * on another.
 */
public class ContactVerificationChallenge {

  ContactVerificationChallengeIdentifier identifier;
  UserIdentifier userIdentifier;
  ContactVerificationOperation operation;
  String targetValue;
  String verificationCode;
  int attempts;
  LocalDateTime expiresAt;

  public ContactVerificationChallenge() {}

  public ContactVerificationChallenge(
      ContactVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier,
      ContactVerificationOperation operation,
      String targetValue,
      String verificationCode,
      int attempts,
      LocalDateTime expiresAt) {
    this.identifier = identifier;
    this.userIdentifier = userIdentifier;
    this.operation = operation;
    this.targetValue = targetValue;
    this.verificationCode = verificationCode;
    this.attempts = attempts;
    this.expiresAt = expiresAt;
  }

  public static ContactVerificationChallenge create(
      UserIdentifier userIdentifier,
      ContactVerificationOperation operation,
      String targetValue,
      String verificationCode,
      int expireSeconds) {
    return new ContactVerificationChallenge(
        new ContactVerificationChallengeIdentifier(java.util.UUID.randomUUID().toString()),
        userIdentifier,
        operation,
        targetValue,
        verificationCode,
        0,
        SystemDateTime.now().plusSeconds(expireSeconds));
  }

  public ContactVerificationChallengeIdentifier identifier() {
    return identifier;
  }

  public UserIdentifier userIdentifier() {
    return userIdentifier;
  }

  public ContactVerificationOperation operation() {
    return operation;
  }

  public String targetValue() {
    return targetValue;
  }

  public int attempts() {
    return attempts;
  }

  /** Exposed for persistence only; comparison goes through {@link #matches(String)}. */
  public String verificationCodeValue() {
    return verificationCode;
  }

  /** Returns a copy with one more failed attempt recorded. */
  public ContactVerificationChallenge countUpAttempts() {
    return new ContactVerificationChallenge(
        identifier,
        userIdentifier,
        operation,
        targetValue,
        verificationCode,
        attempts + 1,
        expiresAt);
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  /**
   * Management-side view (Issue #1416).
   *
   * <p>Includes {@code verification_code} deliberately: redeeming a challenge requires the owner's
   * access token, so an operator holding the code cannot use it on its own, and support needs it to
   * diagnose "the code never arrived" the same way the existing authentication-interaction
   * management API allows.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("user_id", userIdentifier.value());
    map.put("operation", operation.value());
    map.put("target_value", targetValue);
    map.put("verification_code", verificationCode);
    map.put("attempts", attempts);
    map.put("expires_at", expiresAt.toString());
    map.put("expired", isExpired());
    return map;
  }

  /**
   * Audit view: {@link #toMap()} without {@code verification_code}.
   *
   * <p>The code is safe to hand to an operator over a permission-gated request, but the audit log
   * is retained indefinitely, and a live one-time code has no business outliving the challenge
   * there. What the audit log needs to record is which challenge was read, by whom — not its
   * secret.
   */
  public Map<String, Object> toAuditMap() {
    Map<String, Object> map = toMap();
    map.remove("verification_code");
    return map;
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
