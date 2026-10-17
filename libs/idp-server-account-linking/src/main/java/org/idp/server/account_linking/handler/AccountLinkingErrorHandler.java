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

package org.idp.server.account_linking.handler;

import org.idp.server.account_linking.exception.AccountLinkingDuplicateException;
import org.idp.server.account_linking.exception.AccountLinkingInvalidRequestException;
import org.idp.server.account_linking.exception.AccountLinkingNotFoundException;
import org.idp.server.account_linking.exception.AccountLinkingOperatorMismatchException;
import org.idp.server.account_linking.exception.AccountLinkingSessionStateException;
import org.idp.server.account_linking.exception.ExternalIdpRequestFailedException;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Turns a failed linking check into the result it should be answered and recorded as.
 *
 * <p>Kept apart from the handlers because every phase rejects for the same reasons and has to
 * record the same event; without this the mapping is repeated once per phase and drifts.
 *
 * <p>Everything a check raises maps to {@code external_account_link_failed}. That is deliberate:
 * from the outside a mismatched operator, a missing browser binding and a replayed state are the
 * same thing — an attempt to attach an external account to a session that does not own it — and
 * they are only useful as a signal when they count towards one total.
 */
public class AccountLinkingErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(AccountLinkingErrorHandler.class);

  public AccountLinkingResult handle(Exception exception) {

    if (exception instanceof AccountLinkingOperatorMismatchException) {
      log.warn("Account linking rejected: {}", exception.getMessage());
      return linkFailed(AccountLinkingStatus.FORBIDDEN, exception.getMessage());
    }

    if (exception instanceof AccountLinkingSessionStateException) {
      log.warn("Account linking session state rejected: {}", exception.getMessage());
      return linkFailed(AccountLinkingStatus.CONFLICT, exception.getMessage());
    }

    if (exception instanceof AccountLinkingDuplicateException) {
      log.warn("Account linking duplicate rejected: {}", exception.getMessage());
      return linkFailed(AccountLinkingStatus.CONFLICT, exception.getMessage());
    }

    if (exception instanceof AccountLinkingNotFoundException) {
      log.warn("Account linking session not found: {}", exception.getMessage());
      return linkFailed(AccountLinkingStatus.NOT_FOUND, exception.getMessage());
    }

    if (exception instanceof AccountLinkingInvalidRequestException) {
      log.warn("Account linking request rejected: {}", exception.getMessage());
      return linkFailed(AccountLinkingStatus.BAD_REQUEST, exception.getMessage());
    }

    if (exception instanceof ExternalIdpRequestFailedException) {
      log.warn("Account linking external IdP request failed: {}", exception.getMessage());
      return linkFailed(AccountLinkingStatus.BAD_REQUEST, exception.getMessage());
    }

    String message = exception.getMessage() != null ? exception.getMessage() : "unexpected error";
    log.error("Account linking server error: {}", message, exception);
    return AccountLinkingResult.error(
        AccountLinkingStatus.SERVER_ERROR, "server_error", message, null);
  }

  private AccountLinkingResult linkFailed(AccountLinkingStatus status, String description) {
    return AccountLinkingResult.error(
        status, description, DefaultSecurityEventType.external_account_link_failed);
  }
}
