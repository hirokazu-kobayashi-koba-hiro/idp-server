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

package org.idp.server.account_linking.exception;

import org.idp.server.account_linking.io.AccountLinkingStatus;

/**
 * Raised when a stored token retrieval is refused.
 *
 * <p>Carries the status and the OAuth error code it is answered with, so each check names its own
 * answer at the point it fails instead of a mapping table elsewhere guessing it from the type.
 */
public class LinkedExternalAccountTokenException extends RuntimeException {

  AccountLinkingStatus status;
  String error;

  public LinkedExternalAccountTokenException(
      AccountLinkingStatus status, String error, String description) {
    super(description);
    this.status = status;
    this.error = error;
  }

  public static LinkedExternalAccountTokenException invalidRequest(String description) {
    return new LinkedExternalAccountTokenException(
        AccountLinkingStatus.BAD_REQUEST, "invalid_request", description);
  }

  public static LinkedExternalAccountTokenException invalidClient(String description) {
    return new LinkedExternalAccountTokenException(
        AccountLinkingStatus.UNAUTHORIZED, "invalid_client", description);
  }

  public static LinkedExternalAccountTokenException invalidToken(String description) {
    return new LinkedExternalAccountTokenException(
        AccountLinkingStatus.UNAUTHORIZED, "invalid_token", description);
  }

  public static LinkedExternalAccountTokenException unauthorizedClient(String description) {
    return new LinkedExternalAccountTokenException(
        AccountLinkingStatus.FORBIDDEN, "unauthorized_client", description);
  }

  public static LinkedExternalAccountTokenException insufficientScope(String description) {
    return new LinkedExternalAccountTokenException(
        AccountLinkingStatus.FORBIDDEN, "insufficient_scope", description);
  }

  public static LinkedExternalAccountTokenException notFound(String description) {
    return new LinkedExternalAccountTokenException(
        AccountLinkingStatus.NOT_FOUND, "not_found", description);
  }

  public static LinkedExternalAccountTokenException relinkRequired(String description) {
    return new LinkedExternalAccountTokenException(
        AccountLinkingStatus.BAD_REQUEST, "relink_required", description);
  }

  public static LinkedExternalAccountTokenException externalIdpUnavailable(String description) {
    return new LinkedExternalAccountTokenException(
        AccountLinkingStatus.BAD_GATEWAY, "temporarily_unavailable", description);
  }

  public AccountLinkingStatus status() {
    return status;
  }

  public String error() {
    return error;
  }

  public boolean isRelinkRequired() {
    return "relink_required".equals(error);
  }
}
