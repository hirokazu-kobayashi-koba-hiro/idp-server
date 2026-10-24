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

package org.idp.server.account_linking.verifier;

import java.time.LocalDateTime;
import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.account_linking.exception.AccountLinkingInvalidRequestException;
import org.idp.server.account_linking.exception.AccountLinkingNotFoundException;
import org.idp.server.account_linking.exception.AccountLinkingSessionStateException;
import org.idp.server.account_linking.io.AccountLinkingCallbackRequest;

/** Checks that a callback belongs to a link awaiting it, from the browser that started it. */
public class AccountLinkingCallbackVerifier {

  /**
   * A browser that cannot present the secret issued at {@code /linking/start} is not the one that
   * started this link: its authorization code is never redeemed, and a denial from it does not end
   * the link. The session is left for the browser that can.
   */
  public void verify(
      AccountLinkingSession session, AccountLinkingCallbackRequest request, LocalDateTime now) {
    verifySessionExists(session);
    session.verifyNotExpired(now);
    verifyStatusIsAuthorized(session);
    verifyCallbackProviderMatchesSession(session, request);
    session.verifyBrowserBinding(request.browserBindingSecret());
  }

  private void verifySessionExists(AccountLinkingSession session) {
    if (!session.exists()) {
      throw new AccountLinkingNotFoundException("Account linking session not found.");
    }
  }

  /**
   * The link has reached {@code /linking/start} and not yet had its callback.
   *
   * <p>Looked at before the binding. A link that has already moved on is most often the same
   * browser coming back — a reload, or the back button after the cookie was spent — and telling
   * that user the link was started elsewhere would be wrong, as would counting it as a binding
   * mismatch.
   */
  private void verifyStatusIsAuthorized(AccountLinkingSession session) {
    if (!session.status().isAuthorized()) {
      throw new AccountLinkingSessionStateException(
          "Account linking session is not awaiting a callback.");
    }
  }

  /**
   * The callback is the one of the provider the link was started with.
   *
   * <p>Each provider is registered with its own callback, so a response arriving at another
   * provider's is a mix-up: its code was issued by a provider this link never asked, and is not
   * redeemed at this one. (RFC 9207's {@code iss} on the response would tell the same, where the
   * provider sends it.)
   */
  private void verifyCallbackProviderMatchesSession(
      AccountLinkingSession session, AccountLinkingCallbackRequest request) {
    if (!session.provider().equals(request.provider())) {
      throw new AccountLinkingInvalidRequestException(
          "The callback belongs to another provider than the one this link was started with.");
    }
  }
}
