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

  static final long DEFAULT_ACCESS_TOKEN_LIFETIME = 3600;

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

      ParkedCredentials credentials = exchange(tenant, session, request.code(), now);
      sessionCommandRepository.update(tenant, session.park(credentials));

      return AccountLinkingResult.redirect(
          AccountLinkingReturnUri.done(session.redirectUri(), state).value());

    } catch (Exception exception) {
      errorHandler.handle(exception);
      return failed(tenant, session, AccountLinkingReturnError.SERVER_ERROR);
    }
  }

  /**
   * Checks that this callback belongs to a link awaiting it, from the browser that started it.
   *
   * <p>The status is looked at before the binding. A link that has already moved on is most often
   * the same browser coming back — a reload, or the back button after the cookie was spent — and
   * telling that user the link was started elsewhere would be wrong, as would counting it as a
   * binding mismatch.
   *
   * <p>A browser that cannot present the secret issued at {@code /linking/start} is not the one
   * that started this link: its authorization code is never redeemed, and a denial from it does not
   * end the link. The session is left for the browser that can.
   *
   * @return the error page to send the browser to, or {@code null} when the browser is verified
   */
  private AccountLinkingResult verifyBrowser(
      Tenant tenant,
      AccountLinkingSession session,
      AccountLinkingCallbackRequest request,
      LocalDateTime now) {
    try {
      if (!session.exists()) {
        throw new AccountLinkingNotFoundException("Account linking session not found.");
      }
      session.verifyNotExpired(now);
      if (!session.status().isAuthorized()) {
        throw new AccountLinkingSessionStateException(
            "Account linking session is not awaiting a callback.");
      }
      session.verifyBrowserBinding(request.browserBindingSecret());
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

  private ParkedCredentials exchange(
      Tenant tenant, AccountLinkingSession session, String code, LocalDateTime now) {

    OidcSsoConfiguration configuration =
        configurationResolver.resolve(tenant, session.provider()).oidc();

    OidcTokenResult tokenResult =
        tokenGateway.exchangeAuthorizationCode(
            configuration, code, configuration.redirectUri(), session.codeVerifier());

    if (tokenResult.isError() || !tokenResult.hasAccessToken()) {
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

    return new ParkedCredentials.Builder()
        .federatedUserId(federatedUserId)
        .federatedUsername(federatedUsername)
        .grantedScope(tokenResult.hasScope() ? tokenResult.scope() : configuration.scopeAsString())
        .encryptedAccessToken(accessToken)
        .encryptedRefreshToken(refreshToken)
        .encryptionKeyId(DEFAULT_ENCRYPTION_KEY_ID)
        .accessTokenExpiresAt(now.plusSeconds(accessTokenLifetime(tokenResult)))
        .refreshTokenExpiresAt(
            refreshToken == null ? null : now.plusSeconds(configuration.refreshTokenExpiresIn()))
        .build();
  }

  /**
   * The access token's lifetime in seconds.
   *
   * <p>{@code expires_in} is only RECOMMENDED (RFC 6749 §5.1), and some providers leave it out. A
   * token stored without an expiry would count as expired from the start and be refreshed on every
   * retrieval, so an hour is assumed instead; a token that dies earlier is refreshed or reported as
   * needing a new link when it is next used.
   */
  private long accessTokenLifetime(OidcTokenResult tokenResult) {
    return tokenResult.hasExpiresIn() ? tokenResult.expiresIn() : DEFAULT_ACCESS_TOKEN_LIFETIME;
  }
}
