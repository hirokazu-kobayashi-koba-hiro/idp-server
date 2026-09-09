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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Outcome of a self-service email operation (Issue #1416).
 *
 * <p>Carries the audit event alongside the result so the caller publishes what the domain decided
 * rather than re-deriving it from the status.
 */
public class EmailVerificationResponse {

  EmailVerificationStatus status;
  Map<String, Object> contents;
  DefaultSecurityEventType eventType;

  EmailVerificationResponse(
      EmailVerificationStatus status,
      Map<String, Object> contents,
      DefaultSecurityEventType eventType) {
    this.status = status;
    this.contents = contents;
    this.eventType = eventType;
  }

  public static EmailVerificationResponse challengeIssued(
      EmailVerificationChallengeIdentifier identifier, EmailVerificationOperation operation) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("id", identifier.value());
    return new EmailVerificationResponse(
        EmailVerificationStatus.OK, contents, operation.requestSuccessEvent());
  }

  public static EmailVerificationResponse committed(
      Map<String, Object> user, EmailVerificationOperation operation) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("user", user);
    return new EmailVerificationResponse(
        EmailVerificationStatus.OK, contents, operation.successEvent());
  }

  public static EmailVerificationResponse requestFailure(
      String description, EmailVerificationOperation operation) {
    return new EmailVerificationResponse(
        EmailVerificationStatus.INVALID_REQUEST,
        errorContents(description, null),
        operation.requestFailureEvent());
  }

  public static EmailVerificationResponse invalidCandidate(
      String description, List<String> errorMessages, EmailVerificationOperation operation) {
    return new EmailVerificationResponse(
        EmailVerificationStatus.INVALID_REQUEST,
        errorContents(description, errorMessages),
        operation.requestFailureEvent());
  }

  public static EmailVerificationResponse failure(
      String description, EmailVerificationOperation operation) {
    return new EmailVerificationResponse(
        EmailVerificationStatus.INVALID_REQUEST,
        errorContents(description, null),
        operation.failureEvent());
  }

  /**
   * A challenge that does not exist, is not the caller's, or is of the other operation.
   *
   * <p>All three collapse to the same answer so the sibling endpoint cannot be used to probe which
   * challenge ids exist.
   */
  public static EmailVerificationResponse notFound(EmailVerificationOperation operation) {
    return new EmailVerificationResponse(
        EmailVerificationStatus.NOT_FOUND,
        errorContents("email verification challenge not found.", null),
        operation.failureEvent());
  }

  private static Map<String, Object> errorContents(String description, List<String> errorMessages) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", "invalid_request");
    contents.put("error_description", description);
    if (errorMessages != null) {
      contents.put("error_messages", errorMessages);
    }
    return contents;
  }

  public EmailVerificationStatus status() {
    return status;
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public DefaultSecurityEventType eventType() {
    return eventType;
  }

  public boolean isOk() {
    return status.isOk();
  }
}
