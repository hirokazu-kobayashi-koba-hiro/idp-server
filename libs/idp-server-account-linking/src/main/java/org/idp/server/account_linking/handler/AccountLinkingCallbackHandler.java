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

import java.time.LocalDateTime;
import org.idp.server.account_linking.*;
import org.idp.server.account_linking.exception.AccountLinkingSessionStateException;
import org.idp.server.account_linking.io.AccountLinkingCallbackRequest;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.account_linking.verifier.AccountLinkingCallbackVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Checks that the callback comes back to the browser that started the link, and holds what the code
 * obtained ({@link AccountLinkingCredentialsService}) against the session.
 *
 * <p>Reached by a browser navigation from the external IdP with no Bearer token, so this step
 * finalizes nothing. It writes only into the session row; the link itself is written by the
 * Bearer-authenticated complete phase.
 */
public class AccountLinkingCallbackHandler {

  AccountLinkingSessionQueryRepository sessionQueryRepository;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  AccountLinkingCredentialsService credentialsService;
  AccountLinkingCallbackVerifier callbackVerifier;
  AccountLinkingErrorHandler errorHandler;
  LoggerWrapper log = LoggerWrapper.getLogger(AccountLinkingCallbackHandler.class);

  public AccountLinkingCallbackHandler(
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      AccountLinkingCredentialsService credentialsService) {
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.credentialsService = credentialsService;
    this.callbackVerifier = new AccountLinkingCallbackVerifier();
    this.errorHandler = new AccountLinkingErrorHandler();
  }

  public AccountLinkingResult handle(AccountLinkingCallbackRequest request) {

    Tenant tenant = request.tenant();
    AccountLinkingState state = request.state();
    LocalDateTime now = SystemDateTime.now();

    AccountLinkingSession session = sessionQueryRepository.find(tenant, state);
    AccountLinkingResult unverified = verifyBrowser(tenant, session, request, now);
    if (unverified != null) {
      return unverified;
    }

    // From here the browser is the one that started this link, so every failure goes back to the
    // RP with the state, whatever raised it. Only the session is dropped: the external IdP has
    // answered this authorization request, or its code has been spent, and neither can be retried.
    try {
      if (request.hasError()) {
        log.warn(
            "Account linking denied at the external identity provider. error={}",
            AccountLinkingReturnError.fromExternal(request.error()).value());
        return failed(tenant, session, AccountLinkingReturnError.fromExternal(request.error()));
      }
      if (!request.hasCode()) {
        log.warn("Account linking callback carried neither a code nor an error.");
        return failed(tenant, session, AccountLinkingReturnError.SERVER_ERROR);
      }

      // Claimed before the exchange: a request that loses here must not redeem the code, because
      // redeeming a code twice is what makes a provider revoke the grant.
      if (!sessionCommandRepository.claim(
          tenant,
          state,
          AccountLinkingSessionStatus.AUTHORIZED,
          AccountLinkingSessionStatus.PARKED,
          now)) {
        // Another callback of this same browser got here first and is redeeming the code. Leave
        // its session alone and send this one to the error page.
        return errorPage(
            tenant,
            session,
            new AccountLinkingSessionStateException(
                "Account linking session is not awaiting a callback."));
      }

      ParkedCredentials credentials =
          credentialsService.obtain(tenant, session, request.code(), now);
      sessionCommandRepository.update(tenant, session.park(credentials));

      return AccountLinkingResult.redirect(
          AccountLinkingReturnUri.done(session.redirectUri(), state).value());

    } catch (Exception exception) {
      errorHandler.handle(exception);
      return failed(tenant, session, AccountLinkingReturnError.SERVER_ERROR);
    }
  }

  /**
   * The error page for a callback that is not this link's, from this browser; {@code null} when it
   * is.
   */
  private AccountLinkingResult verifyBrowser(
      Tenant tenant,
      AccountLinkingSession session,
      AccountLinkingCallbackRequest request,
      LocalDateTime now) {
    try {
      callbackVerifier.verify(session, request, now);
      return null;
    } catch (Exception exception) {
      return errorPage(tenant, session, exception);
    }
  }

  private AccountLinkingResult errorPage(
      Tenant tenant, AccountLinkingSession session, Exception exception) {
    AccountLinkingResult failure = errorHandler.handle(exception);
    AccountLinkingResult redirect =
        failure.redirectingTo(new AccountLinkingErrorPage(tenant, failure.status()).value());
    return session.exists() ? withSessionContext(redirect, session) : redirect;
  }

  /** Drops the session and sends the verified browser back to the RP with the state. */
  private AccountLinkingResult failed(
      Tenant tenant, AccountLinkingSession session, AccountLinkingReturnError error) {
    sessionCommandRepository.delete(tenant, session.state());

    return withSessionContext(
        AccountLinkingResult.redirect(
            AccountLinkingReturnUri.failed(session.redirectUri(), error, session.state()).value(),
            DefaultSecurityEventType.external_account_link_failed),
        session);
  }

  /**
   * Names the user and client the link belongs to on the event. A run of failures is only useful if
   * it says whose link was being targeted.
   */
  private AccountLinkingResult withSessionContext(
      AccountLinkingResult result, AccountLinkingSession session) {
    return result.withContext(
        session.requestedClientId(), new User().setSub(session.userIdentifier().value()));
  }
}
