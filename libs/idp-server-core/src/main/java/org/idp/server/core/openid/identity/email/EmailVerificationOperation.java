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

import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * The two self-service email operations (Issue #1416).
 *
 * <p>They are separate operations, not one operation with a comparison, because they carry
 * different privilege: {@link #VERIFY} only sets {@code email_verified} on the address already on
 * the account, whereas {@link #CHANGE} replaces {@code email} and — under an EMAIL identity policy
 * — moves {@code preferred_username}, the login identifier. The required scope therefore differs,
 * and the authorization decision has to be made before any code is sent, which is impossible if the
 * intent is inferred from comparing a request-supplied address to the current one.
 *
 * <p>The operation is persisted on the challenge row, so what was authorized and what is committed
 * cannot drift apart.
 */
public enum EmailVerificationOperation {
  VERIFY(
      "verify",
      "openid",
      "email_verify",
      DefaultSecurityEventType.email_verify_request_success,
      DefaultSecurityEventType.email_verify_request_failure,
      DefaultSecurityEventType.email_verify_success,
      DefaultSecurityEventType.email_verify_failure),
  CHANGE(
      "change",
      "email:change",
      "email_change",
      DefaultSecurityEventType.email_change_request_success,
      DefaultSecurityEventType.email_change_request_failure,
      DefaultSecurityEventType.email_change_success,
      DefaultSecurityEventType.email_change_failure);

  String value;
  String requiredScope;
  String templateKey;
  DefaultSecurityEventType requestSuccessEvent;
  DefaultSecurityEventType requestFailureEvent;
  DefaultSecurityEventType successEvent;
  DefaultSecurityEventType failureEvent;

  EmailVerificationOperation(
      String value,
      String requiredScope,
      String templateKey,
      DefaultSecurityEventType requestSuccessEvent,
      DefaultSecurityEventType requestFailureEvent,
      DefaultSecurityEventType successEvent,
      DefaultSecurityEventType failureEvent) {
    this.value = value;
    this.requiredScope = requiredScope;
    this.templateKey = templateKey;
    this.requestSuccessEvent = requestSuccessEvent;
    this.requestFailureEvent = requestFailureEvent;
    this.successEvent = successEvent;
    this.failureEvent = failureEvent;
  }

  public static EmailVerificationOperation of(String value) {
    for (EmailVerificationOperation operation : values()) {
      if (operation.value.equals(value)) {
        return operation;
      }
    }
    throw new UnSupportedException(String.format("unsupported email operation (%s)", value));
  }

  public String value() {
    return value;
  }

  /**
   * The OAuth scope a token must carry to drive this operation.
   *
   * <p>{@code openid} is enough to prove you still hold the address already on the account — the
   * same bar as password change and account deletion. Moving the login identifier needs its own
   * scope: gating it on {@code openid} would hand identifier takeover to every ordinary OIDC token,
   * including one issued to a third-party client that was never granted it.
   */
  public String requiredScope() {
    return requiredScope;
  }

  /** Email template key, falling back to the configuration's default when undefined. */
  public String templateKey() {
    return templateKey;
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

  public boolean isVerify() {
    return this == VERIFY;
  }

  public boolean isChange() {
    return this == CHANGE;
  }
}
