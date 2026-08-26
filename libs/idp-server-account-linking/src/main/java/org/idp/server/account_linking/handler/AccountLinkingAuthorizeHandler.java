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
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
  AccountLinkingErrorHandler errorHandler;

  public AccountLinkingAuthorizeHandler(
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      AccountLinkingConfigurationResolver configurationResolver) {
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.configurationResolver = configurationResolver;
    this.errorHandler = new AccountLinkingErrorHandler();
  }

  public AccountLinkingAuthorization handle(AccountLinkingAuthorizeRequest request) {

    Tenant tenant = request.tenant();
    AccountLinkingState state = request.state();
    LocalDateTime now = SystemDateTime.now();

    try {
      AccountLinkingSession session = getSession(tenant, state);
      session.verifyNotExpired(now);

      AccountLinkingBrowserBinding browserBinding = AccountLinkingBrowserBinding.generate();
      AccountLinkingSession authorized =
          session.authorize(request.operator().userIdentifier(), browserBinding, null, "pwd", now);

      if (!sessionCommandRepository.claim(
          tenant, state, AccountLinkingSessionStatus.PENDING, authorized.status(), now)) {
        throw new AccountLinkingSessionStateException(
            "Account linking session was already started.");
      }
      sessionCommandRepository.update(tenant, authorized);

      OidcSsoConfiguration configuration =
          configurationResolver.oidc(tenant, authorized.provider());

      return AccountLinkingAuthorization.of(
          AccountLinkingResult.redirect(externalAuthorizationUri(configuration, authorized)),
          browserBinding.secret());

    } catch (Exception exception) {
      return AccountLinkingAuthorization.error(
          errorHandler
              .handle(exception)
              .withContext(new RequestedClientId(""), request.operator()));
    }
  }

  private AccountLinkingSession getSession(Tenant tenant, AccountLinkingState state) {
    AccountLinkingSession session = sessionQueryRepository.find(tenant, state);
    if (!session.exists()) {
      throw new AccountLinkingNotFoundException("Account linking session not found.");
    }
    return session;
  }

  private String externalAuthorizationUri(
      OidcSsoConfiguration configuration, AccountLinkingSession session) {
    AccountLinkingPkce pkce = new AccountLinkingPkce(session.codeVerifier());

    HttpQueryParams params = new HttpQueryParams();
    params.add("client_id", configuration.clientId());
    params.add("redirect_uri", configuration.redirectUri());
    params.add("response_type", "code");
    params.add("state", session.state().value());
    params.add("nonce", session.nonce());
    params.add(
        "scope",
        session.requestedScope() == null
            ? configuration.scopeAsString()
            : session.requestedScope());
    params.add("code_challenge", pkce.codeChallenge());
    params.add("code_challenge_method", pkce.codeChallengeMethod());

    return String.format("%s?%s", configuration.authorizationEndpoint(), params.params());
  }
}
