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

package org.idp.server.core.openid.identity.contact.execution;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionStatus;

/**
 * Outcome of one contact execution step (Issue #1416).
 *
 * <p>{@code status} and {@code contents} mirror {@code AuthenticationExecutionResult}: success is
 * decided by the status the HTTP execution resolved to. That is not the same as the raw transport
 * status — a service that answers {@code 200} with a negative verdict in the body is mapped by the
 * request's own {@code response_resolve_configs} (conditions over {@code $.status_code} / {@code
 * $.response_body.*} producing a {@code mapped_status_code}), which is what reaches here. So the
 * verdict stays the tenant's to describe, in the same place and the same way as on the login path.
 *
 * <p>The two extra fields carry what the challenge row has to keep, and only one of them is ever
 * set: a locally generated code, or the reference an external service will expect back. Which one
 * is filled is the executor's answer, not something the caller infers from configuration.
 */
public class ContactExecutionResult {

  AuthenticationExecutionStatus status;
  Map<String, Object> contents;
  String verificationCode;
  Map<String, Object> storedPayload;

  ContactExecutionResult(
      AuthenticationExecutionStatus status,
      Map<String, Object> contents,
      String verificationCode,
      Map<String, Object> storedPayload) {
    this.status = status;
    this.contents = contents == null ? new HashMap<>() : contents;
    this.verificationCode = verificationCode;
    this.storedPayload = storedPayload == null ? new HashMap<>() : storedPayload;
  }

  public static ContactExecutionResult success(Map<String, Object> contents) {
    return new ContactExecutionResult(AuthenticationExecutionStatus.OK, contents, null, null);
  }

  /** idp-server generated and delivered the code; the row keeps it. */
  public static ContactExecutionResult successWithCode(
      Map<String, Object> contents, String verificationCode) {
    return new ContactExecutionResult(
        AuthenticationExecutionStatus.OK, contents, verificationCode, null);
  }

  /**
   * An external service holds the code; the row keeps whatever {@code http_request_store} mapped
   * out of its answer.
   */
  public static ContactExecutionResult successWithPayload(
      Map<String, Object> contents, Map<String, Object> storedPayload) {
    return new ContactExecutionResult(
        AuthenticationExecutionStatus.OK, contents, null, storedPayload);
  }

  public static ContactExecutionResult clientError(Map<String, Object> contents) {
    return new ContactExecutionResult(
        AuthenticationExecutionStatus.CLIENT_ERROR, contents, null, null);
  }

  public static ContactExecutionResult error(int statusCode, Map<String, Object> contents) {
    return new ContactExecutionResult(
        AuthenticationExecutionStatus.fromStatusCode(statusCode), contents, null, null);
  }

  public boolean isSuccess() {
    return status.isOk();
  }

  public int statusCode() {
    return status.code();
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public String verificationCode() {
    return verificationCode;
  }

  public Map<String, Object> storedPayload() {
    return storedPayload;
  }

  /** Whether the code lives in an external service rather than on the challenge row. */
  public boolean isExternal() {
    return verificationCode == null || verificationCode.isBlank();
  }
}
