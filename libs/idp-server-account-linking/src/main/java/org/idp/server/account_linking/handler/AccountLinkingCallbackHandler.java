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
import org.idp.server.account_linking.exception.ExternalIdpRequestFailedException;
import org.idp.server.account_linking.gateway.ExternalIdpIdTokenGateway;
import org.idp.server.account_linking.gateway.ExternalIdpTokenGateway;
import org.idp.server.account_linking.gateway.ExternalIdpUserinfoGateway;
import org.idp.server.account_linking.io.AccountLinkingCallbackRequest;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.federation.sso.oidc.OidcTokenResult;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Exchanges the authorization code and holds the result against the session.
 *
 * <p>Reached by a browser navigation from the external IdP with no Bearer token, so this step
 * finalizes nothing. It writes only into the session row; the link itself is written by the
 * Bearer-authenticated complete phase.
 */
public class AccountLinkingCallbackHandler {

  static final String DEFAULT_ENCRYPTION_KEY_ID = "default";

  AccountLinkingSessionQueryRepository sessionQueryRepository;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  AccountLinkingConfigurationResolver configurationResolver;
  ExternalIdpTokenGateway tokenGateway;
  ExternalIdpIdTokenGateway idTokenGateway;
  ExternalIdpUserinfoGateway userinfoGateway;
  AesCipher aesCipher;
  AccountLinkingErrorHandler errorHandler;
  LoggerWrapper log = LoggerWrapper.getLogger(AccountLinkingCallbackHandler.class);

  public AccountLinkingCallbackHandler(
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      AccountLinkingConfigurationResolver configurationResolver,
      ExternalIdpTokenGateway tokenGateway,
      ExternalIdpIdTokenGateway idTokenGateway,
      ExternalIdpUserinfoGateway userinfoGateway,
      AesCipher aesCipher) {
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.configurationResolver = configurationResolver;
    this.tokenGateway = tokenGateway;
    this.idTokenGateway = idTokenGateway;
    this.userinfoGateway = userinfoGateway;
    this.aesCipher = aesCipher;
    this.errorHandler = new AccountLinkingErrorHandler();
  }

  public AccountLinkingResult handle(AccountLinkingCallbackRequest request) {

    Tenant tenant = request.tenant();
    AccountLinkingState state = request.state();

    if (request.hasError()) {
      log.warn(
          "Account linking denied at the external identity provider. error={}", request.error());
      return AccountLinkingResult.error(
          AccountLinkingStatus.BAD_REQUEST,
          request.error(),
          request.errorDescription(),
          DefaultSecurityEventType.external_account_link_failed,
          null);
    }

    LocalDateTime now = SystemDateTime.now();

    try {
      AccountLinkingSession session = getSession(tenant, state);
      session.verifyNotExpired(now);

      // Before anything else, and before the session is claimed: a browser that cannot present the
      // secret issued at /linking/start is not the one that started this link, and its
      // authorization code must not be redeemed. Rejecting here leaves the session usable by the
      // browser that can.
      session.verifyBrowserBinding(request.browserBindingSecret());

      // Claimed before the exchange: a request that loses here must not redeem the code, because
      // redeeming a code twice is what makes a provider revoke the grant.
      if (!sessionCommandRepository.claim(
          tenant,
          state,
          AccountLinkingSessionStatus.AUTHORIZED,
          AccountLinkingSessionStatus.PARKED,
          now)) {
        throw new AccountLinkingSessionStateException(
            "Account linking session is not awaiting a callback.");
      }

      ParkedCredentials credentials = exchange(tenant, session, request.code(), now);
      sessionCommandRepository.update(tenant, session.park(credentials));

      return AccountLinkingResult.redirect(session.redirectUri());

    } catch (ExternalIdpRequestFailedException exception) {
      // The code is spent and the session cannot be retried, so it is dropped rather than left
      // parked with nothing in it.
      sessionCommandRepository.delete(tenant, state);
      return errorHandler.handle(exception);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  private ParkedCredentials exchange(
      Tenant tenant, AccountLinkingSession session, String code, LocalDateTime now) {

    OidcSsoConfiguration configuration = configurationResolver.oidc(tenant, session.provider());

    OidcTokenResult tokenResult =
        tokenGateway.exchangeAuthorizationCode(
            configuration, code, configuration.redirectUri(), session.codeVerifier());

    if (tokenResult.isError()) {
      log.warn(
          "Account linking token exchange failed. provider={}, status={}",
          session.provider().value(),
          tokenResult.statusCode());
      throw new ExternalIdpRequestFailedException(
          "Account linking failed at the external identity provider token endpoint.");
    }

    // The provider identifies the account when it issues an id_token; its verified sub is the only
    // value it asserts about which account granted access. A plain OAuth 2.0 delegation says
    // nothing, and a link does not need it to be keyed.
    String federatedUserId = null;
    if (tokenResult.hasIdToken()) {
      federatedUserId = idTokenGateway.verifiedSubject(configuration, session, tokenResult);
    }

    String federatedUsername = null;
    if (configuration.hasUserinfo()) {
      User federatedUser = userinfoGateway.request(configuration, tokenResult.accessToken());
      federatedUsername = federatedUser.email();
      if (federatedUserId == null) {
        federatedUserId = federatedUser.externalUserId();
      }
    }

    EncryptedData accessToken = aesCipher.encrypt(tokenResult.accessToken());
    EncryptedData refreshToken =
        tokenResult.hasRefreshToken() ? aesCipher.encrypt(tokenResult.refreshToken()) : null;

    return new ParkedCredentials(
        federatedUserId,
        federatedUsername,
        tokenResult.hasScope() ? tokenResult.scope() : configuration.scopeAsString(),
        accessToken,
        refreshToken,
        DEFAULT_ENCRYPTION_KEY_ID,
        now.plusSeconds(tokenResult.expiresIn()),
        refreshToken == null ? null : now.plusSeconds(configuration.refreshTokenExpiresIn()));
  }

  private AccountLinkingSession getSession(Tenant tenant, AccountLinkingState state) {
    AccountLinkingSession session = sessionQueryRepository.find(tenant, state);
    if (!session.exists()) {
      throw new AccountLinkingNotFoundException("Account linking session not found.");
    }
    return session;
  }
}
