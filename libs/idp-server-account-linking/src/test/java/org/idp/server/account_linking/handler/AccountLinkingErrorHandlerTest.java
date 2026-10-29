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

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.account_linking.exception.*;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.junit.jupiter.api.Test;

/** Each rejection carries its own error code, so an RP can branch without reading the text. */
class AccountLinkingErrorHandlerTest {

  AccountLinkingErrorHandler handler = new AccountLinkingErrorHandler();

  void assertAnswer(Exception exception, int status, String error) {
    AccountLinkingResult result = handler.handle(exception);
    assertEquals(status, result.statusCode());
    assertEquals(error, result.contents().get("error"));
    assertTrue(handler.isRejection(exception));
  }

  @Test
  void anExpiredLinkIsABadRequest() {
    assertAnswer(new AccountLinkingSessionExpiredException("expired"), 400, "expired_link");
  }

  @Test
  void aLinkAnotherRequestMovedOnIsAConflict() {
    assertAnswer(new AccountLinkingSessionStateException("moved on"), 409, "invalid_link_state");
  }

  @Test
  void anAccountLinkedToAnotherUserIsAConflict() {
    assertAnswer(new AccountLinkingDuplicateException("duplicate"), 409, "account_already_linked");
  }

  @Test
  void anotherAccountOfTheProviderIsAConflict() {
    assertAnswer(
        new AccountLinkingAnotherAccountLinkedException("another"), 409, "another_account_linked");
  }

  @Test
  void severalAccountsOfTheProviderAreAConflict() {
    assertAnswer(
        new AccountLinkingSeveralAccountsLinkedException("several"),
        409,
        "several_accounts_linked");
  }

  @Test
  void anotherClientIsForbidden() {
    assertAnswer(new AccountLinkingClientMismatchException("client"), 403, "client_mismatch");
  }

  @Test
  void anotherUserIsDenied() {
    assertAnswer(new AccountLinkingOperatorMismatchException("user"), 403, "access_denied");
  }

  @Test
  void aProviderGoneSinceTheLinkStartedIsUnavailable() {
    assertAnswer(new FederationConfigurationNotFoundException("gone"), 400, "provider_unavailable");
  }
}
