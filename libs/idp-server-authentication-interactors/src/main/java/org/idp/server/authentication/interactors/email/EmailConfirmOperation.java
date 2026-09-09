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

package org.idp.server.authentication.interactors.email;

import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * The two self-service email operations (Issue #1416), keyed by the transaction's auth flow.
 *
 * <p>Deriving the operation from the flow — rather than from comparing a request-supplied address
 * to the current one — is what lets the two endpoints carry different scope requirements: the
 * authorization decision has to be made before any code is sent, and a flow is fixed when the
 * transaction is minted by the token-authenticated {@code /v1/me} entry.
 *
 * <p>Each operation carries its own audit event types so the log distinguishes "the user proved
 * they still hold their address" from "the login identifier moved". Both are distinct from the
 * login-time {@code email_verification_*} events.
 */
public enum EmailConfirmOperation {
  VERIFY(
      StandardAuthFlow.EMAIL_VERIFY,
      "email_verify",
      DefaultSecurityEventType.email_verify_request_success,
      DefaultSecurityEventType.email_verify_request_failure,
      DefaultSecurityEventType.email_verify_success,
      DefaultSecurityEventType.email_verify_failure),
  CHANGE(
      StandardAuthFlow.EMAIL_CHANGE,
      "email_change",
      DefaultSecurityEventType.email_change_request_success,
      DefaultSecurityEventType.email_change_request_failure,
      DefaultSecurityEventType.email_change_success,
      DefaultSecurityEventType.email_change_failure);

  StandardAuthFlow authFlow;
  String templateKey;
  DefaultSecurityEventType requestSuccessEvent;
  DefaultSecurityEventType requestFailureEvent;
  DefaultSecurityEventType successEvent;
  DefaultSecurityEventType failureEvent;

  EmailConfirmOperation(
      StandardAuthFlow authFlow,
      String templateKey,
      DefaultSecurityEventType requestSuccessEvent,
      DefaultSecurityEventType requestFailureEvent,
      DefaultSecurityEventType successEvent,
      DefaultSecurityEventType failureEvent) {
    this.authFlow = authFlow;
    this.templateKey = templateKey;
    this.requestSuccessEvent = requestSuccessEvent;
    this.requestFailureEvent = requestFailureEvent;
    this.successEvent = successEvent;
    this.failureEvent = failureEvent;
  }

  /**
   * Returns the operation the given flow drives, or {@code null} when the flow is not one of the
   * two email-confirm flows. Callers treat {@code null} as "this interactor must not run here" —
   * the guard that keeps login / CIBA transactions out of the email-confirm interactors.
   */
  public static EmailConfirmOperation of(AuthFlow authFlow) {
    for (EmailConfirmOperation operation : values()) {
      if (operation.authFlow.toAuthFlow().equals(authFlow)) {
        return operation;
      }
    }
    return null;
  }

  public boolean isVerify() {
    return this == VERIFY;
  }

  public boolean isChange() {
    return this == CHANGE;
  }

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
}
