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
import org.idp.server.account_linking.exception.AccountLinkingNotFoundException;
import org.idp.server.account_linking.exception.AccountLinkingSessionStateException;
import org.idp.server.account_linking.io.AccountLinkingAuthorizeRequest;
import org.idp.server.account_linking.io.AccountLinkingAuthorizeResult;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Verifies the operator and hands back the external authorization URL.
 *
 * <p>This is where an attacker luring a victim through the attacker's own session is stopped:
 * afterwards the attacker still holds the state, so the check at complete would pass. It also
 * issues the browser binding the callback will require, which covers the reverse — an attacker who
 * walks this step himself and forwards only the external URL.
 */
public class AccountLinkingAuthorizeHandler {

  AccountLinkingSessionQueryRepository sessionQueryRepository;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  AccountLinkingConfigurationResolver configurationResolver;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  AccountLinkingErrorHandler errorHandler;

  public AccountLinkingAuthorizeHandler(
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      AccountLinkingConfigurationResolver configurationResolver,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository) {
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.configurationResolver = configurationResolver;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.errorHandler = new AccountLinkingErrorHandler();
  }

  public AccountLinkingAuthorizeResult handle(AccountLinkingAuthorizeRequest request) {

    Tenant tenant = request.tenant();
    AccountLinkingState state = request.state();
    LocalDateTime now = SystemDateTime.now();

    try {
      AccountLinkingSession session = getSession(tenant, state);
      session.verifyNotExpired(now);
      // Someone else's browser is turned away before anything else: sending it to sign in as the
      // link's user would only show that user's sign-in page to a stranger.
      session.verifyOperator(request.operator().userIdentifier());

      ClientConfiguration client =
          clientConfigurationQueryRepository.get(tenant, session.requestedClientId());
      if (!LinkingSignInRequirement.of(client).isMetBy(request.authentication(), session, now)) {
        return AccountLinkingAuthorizeResult.of(
            request.returnedFromSignIn()
                ? loginRequired(session)
                : signInAgain(tenant, client, session),
            null);
      }

      AccountLinkingBrowserBinding browserBinding = AccountLinkingBrowserBinding.generate();
      AccountLinkingSession authorized =
          session.authorize(
              request.operator().userIdentifier(), browserBinding, request.authentication());

      // Resolved before the session is claimed, so a provider removed since link leaves the
      // session as it was instead of started with nowhere to go.
      OidcSsoConfiguration configuration =
          configurationResolver.resolve(tenant, authorized.provider()).oidc();

      if (!sessionCommandRepository.claim(
          tenant, state, AccountLinkingSessionStatus.PENDING, authorized.status(), now)) {
        throw new AccountLinkingSessionStateException(
            "Account linking session was already started.");
      }
      sessionCommandRepository.update(tenant, authorized);

      return AccountLinkingAuthorizeResult.of(
          AccountLinkingResult.redirect(
              new AccountLinkingExternalAuthorizationUri(configuration, authorized).value()),
          browserBinding.secret());

    } catch (Exception exception) {
      // The operator has not been shown to own this link, or the link is gone: the error page,
      // not the RP. Sending a victim lured through someone else's link back to the RP would hand
      // that link to the victim's session there.
      AccountLinkingResult failure = errorHandler.handle(exception);
      return AccountLinkingAuthorizeResult.error(
          failure
              .redirectingTo(new AccountLinkingErrorPage(tenant, failure.status()).value())
              .withContext(new RequestedClientId(""), request.operator()));
    }
  }

  /**
   * {@code /linking/start} reached without a browser session at this server.
   *
   * <p>Nobody can be verified as the operator, so the link is left as it is for a browser that can
   * be, and the RP is told only that a sign-in is needed. The state is not sent: this browser has
   * not been shown to belong to the user who started the link.
   *
   * <p>Not recorded as a failed link. A session that simply ran out is everyday, and counting it
   * would bury the mismatches that event exists to surface.
   */
  public AccountLinkingResult handleWithoutBrowserSession(
      Tenant tenant, AccountLinkingState state, boolean returnedFromSignIn) {
    AccountLinkingSession session = sessionQueryRepository.find(tenant, state);
    LocalDateTime now = SystemDateTime.now();

    if (!session.exists() || session.isExpired(now) || !session.status().isPending()) {
      return AccountLinkingResult.redirect(
          new AccountLinkingErrorPage(tenant, AccountLinkingStatus.NOT_FOUND).value(),
          DefaultSecurityEventType.external_account_link_failed);
    }
    if (returnedFromSignIn) {
      return loginRequired(session);
    }
    try {
      ClientConfiguration client =
          clientConfigurationQueryRepository.get(tenant, session.requestedClientId());
      return signInAgain(tenant, client, session);
    } catch (Exception exception) {
      AccountLinkingResult failure = errorHandler.handle(exception);
      return failure.redirectingTo(new AccountLinkingErrorPage(tenant, failure.status()).value());
    }
  }

  /** Sends the browser to sign in again as the link's user ({@link AccountLinkingSignInUri}). */
  private AccountLinkingResult signInAgain(
      Tenant tenant, ClientConfiguration client, AccountLinkingSession session) {
    AuthorizationServerConfiguration server =
        authorizationServerConfigurationQueryRepository.get(tenant);
    return AccountLinkingResult.redirect(
        new AccountLinkingSignInUri(server.authorizationEndpoint(), client, session).value());
  }

  /**
   * The sign-in did not happen, or did not reach the client's {@code linking_acr_values}: the RP is
   * told a sign-in is needed, without the state.
   */
  private static AccountLinkingResult loginRequired(AccountLinkingSession session) {
    return AccountLinkingResult.redirect(
        AccountLinkingReturnUri.failedUnverified(
                session.redirectUri(), AccountLinkingReturnError.LOGIN_REQUIRED)
            .value());
  }

  private AccountLinkingSession getSession(Tenant tenant, AccountLinkingState state) {
    AccountLinkingSession session = sessionQueryRepository.find(tenant, state);
    if (!session.exists()) {
      throw new AccountLinkingNotFoundException("Account linking session not found.");
    }
    return session;
  }
}
