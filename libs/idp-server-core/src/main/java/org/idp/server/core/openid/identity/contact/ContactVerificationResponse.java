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
public class ContactVerificationResponse {

  ContactVerificationStatus status;
  Map<String, Object> contents;
  DefaultSecurityEventType eventType;

  ContactVerificationResponse(
      ContactVerificationStatus status,
      Map<String, Object> contents,
      DefaultSecurityEventType eventType) {
    this.status = status;
    this.contents = contents;
    this.eventType = eventType;
  }

  public static ContactVerificationResponse challengeIssued(
      ContactVerificationChallengeIdentifier identifier, ContactVerificationOperation operation) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("id", identifier.value());
    return new ContactVerificationResponse(
        ContactVerificationStatus.OK, contents, operation.requestSuccessEvent());
  }

  public static ContactVerificationResponse committed(
      Map<String, Object> user, ContactVerificationOperation operation) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("user", user);
    return new ContactVerificationResponse(
        ContactVerificationStatus.OK, contents, operation.successEvent());
  }

  public static ContactVerificationResponse requestFailure(
      String description, ContactVerificationOperation operation) {
    return new ContactVerificationResponse(
        ContactVerificationStatus.INVALID_REQUEST,
        errorContents(description, null),
        operation.requestFailureEvent());
  }

  public static ContactVerificationResponse invalidCandidate(
      String description, List<String> errorMessages, ContactVerificationOperation operation) {
    return new ContactVerificationResponse(
        ContactVerificationStatus.INVALID_REQUEST,
        errorContents(description, errorMessages),
        operation.requestFailureEvent());
  }

  public static ContactVerificationResponse failure(
      String description, ContactVerificationOperation operation) {
    return new ContactVerificationResponse(
        ContactVerificationStatus.INVALID_REQUEST,
        errorContents(description, null),
        operation.failureEvent());
  }

  /**
   * A challenge that does not exist, is not the caller's, or is of the other operation.
   *
   * <p>All three collapse to the same answer so the sibling endpoint cannot be used to probe which
   * challenge ids exist.
   */
  public static ContactVerificationResponse notFound(ContactVerificationOperation operation) {
    return new ContactVerificationResponse(
        ContactVerificationStatus.NOT_FOUND,
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

  /**
   * The insufficient-scope answer, so the caller does not assemble it (Issue #1416).
   *
   * <p>Deliberately carries no audit event: the request never reached the domain, and the token
   * failing a scope check is a transport-level rejection, not a contact operation that failed.
   */
  public static ContactVerificationResponse insufficientScope(String requiredScope) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", "insufficient_scope");
    contents.put(
        "error_description", String.format("The request requires '%s' scope", requiredScope));
    contents.put("scope", requiredScope);
    return new ContactVerificationResponse(ContactVerificationStatus.FORBIDDEN, contents, null);
  }

  public int statusCode() {
    return status.statusCode();
  }

  public ContactVerificationStatus status() {
    return status;
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public DefaultSecurityEventType eventType() {
    return eventType;
  }

  /** False for a rejection that never reached the domain, such as a scope failure. */
  public boolean hasEventType() {
    return eventType != null;
  }

  public boolean isOk() {
    return status.isOk();
  }
}
