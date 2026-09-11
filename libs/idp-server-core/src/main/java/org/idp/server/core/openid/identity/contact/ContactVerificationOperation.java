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

import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * The four self-service contact operations (Issue #1416): verify or change, for email or phone.
 *
 * <p>Everything that differs between them hangs off this enum — required scope, template key, which
 * column is written, which audit events are emitted — so {@link ContactVerificationService}
 * contains no channel-specific branching beyond picking a repository method.
 *
 * <h2>Why verify and change are separate operations</h2>
 *
 * They carry different privilege. A verification only sets {@code *_verified} on the value already
 * on the account; a change replaces the value and, under a matching identity policy, moves {@code
 * preferred_username} — the login identifier. The authorization decision therefore has to be made
 * before any code is sent, which is impossible if the intent is inferred from comparing a
 * request-supplied value to the current one. Gating a change on {@code openid} would hand
 * identifier takeover to every ordinary OIDC token, including one issued to a third-party client.
 *
 * <p>The operation is persisted on the challenge row, so what was authorized and what is committed
 * cannot drift apart.
 */
public enum ContactVerificationOperation {
  EMAIL_VERIFY(
      "email_verify",
      ContactChannel.EMAIL,
      false,
      "openid",
      "email_verify",
      DefaultSecurityEventType.email_verify_request_success,
      DefaultSecurityEventType.email_verify_request_failure,
      DefaultSecurityEventType.email_verify_success,
      DefaultSecurityEventType.email_verify_failure),
  EMAIL_CHANGE(
      "email_change",
      ContactChannel.EMAIL,
      true,
      "email:change",
      "email_change",
      DefaultSecurityEventType.email_change_request_success,
      DefaultSecurityEventType.email_change_request_failure,
      DefaultSecurityEventType.email_change_success,
      DefaultSecurityEventType.email_change_failure),
  PHONE_VERIFY(
      "phone_verify",
      ContactChannel.PHONE,
      false,
      "openid",
      "phone_verify",
      DefaultSecurityEventType.phone_verify_request_success,
      DefaultSecurityEventType.phone_verify_request_failure,
      DefaultSecurityEventType.phone_verify_success,
      DefaultSecurityEventType.phone_verify_failure),
  PHONE_CHANGE(
      "phone_change",
      ContactChannel.PHONE,
      true,
      "phone:change",
      "phone_change",
      DefaultSecurityEventType.phone_change_request_success,
      DefaultSecurityEventType.phone_change_request_failure,
      DefaultSecurityEventType.phone_change_success,
      DefaultSecurityEventType.phone_change_failure);

  String value;
  ContactChannel channel;
  boolean change;
  String requiredScope;
  String templateKey;
  DefaultSecurityEventType requestSuccessEvent;
  DefaultSecurityEventType requestFailureEvent;
  DefaultSecurityEventType successEvent;
  DefaultSecurityEventType failureEvent;

  ContactVerificationOperation(
      String value,
      ContactChannel channel,
      boolean change,
      String requiredScope,
      String templateKey,
      DefaultSecurityEventType requestSuccessEvent,
      DefaultSecurityEventType requestFailureEvent,
      DefaultSecurityEventType successEvent,
      DefaultSecurityEventType failureEvent) {
    this.value = value;
    this.channel = channel;
    this.change = change;
    this.requiredScope = requiredScope;
    this.templateKey = templateKey;
    this.requestSuccessEvent = requestSuccessEvent;
    this.requestFailureEvent = requestFailureEvent;
    this.successEvent = successEvent;
    this.failureEvent = failureEvent;
  }

  public static ContactVerificationOperation of(String value) {
    for (ContactVerificationOperation operation : values()) {
      if (operation.value.equals(value)) {
        return operation;
      }
    }
    throw new UnSupportedException(String.format("unsupported contact operation (%s)", value));
  }

  public String value() {
    return value;
  }

  public ContactChannel channel() {
    return channel;
  }

  public boolean isChange() {
    return change;
  }

  public boolean isVerify() {
    return !change;
  }

  /**
   * The OAuth scope a token must carry to drive this operation.
   *
   * <p>{@code openid} is enough to prove the value already on the account is reachable — the same
   * bar as password change and account deletion. Replacing it needs its own scope.
   */
  public String requiredScope() {
    return requiredScope;
  }

  /** Message template key; an undefined key falls back to the configuration's default body. */
  public String templateKey() {
    return templateKey;
  }

  /** The value currently on the account for this channel. */
  public String currentValue(User user) {
    return channel.currentValue(user);
  }

  /** Whether the account has a value for this channel at all. */
  public boolean hasCurrentValue(User user) {
    String current = currentValue(user);
    return current != null && !current.isBlank();
  }

  /** Applies the proven value to the user. Only meaningful for a change. */
  public void applyTarget(User user, String targetValue) {
    channel.applyValue(user, targetValue);
  }

  /** Marks this channel verified. */
  public void markVerified(User user) {
    channel.markVerified(user);
  }

  /** Classpath schema validating a change candidate for this channel. */
  public String targetSchemaPath() {
    return channel.targetSchemaPath();
  }

  public DefaultSecurityEventType requestSuccessEvent() {
    return requestSuccessEvent;
  }

  public DefaultSecurityEventType requestFailureEvent() {
    return requestFailureEvent;
  }

  public DefaultSecurityEventType successEvent() {
    return successEvent;
  }

  public DefaultSecurityEventType failureEvent() {
    return failureEvent;
  }
}
