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

package org.idp.server.account_linking;

import java.time.LocalDateTime;
import java.util.List;
import org.idp.server.account_linking.exception.AccountLinkingSessionStateException;
import org.idp.server.account_linking.repository.*;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.mapper.UserInfoMapper;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.federation.sso.oidc.*;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.exception.BadRequestException;
import org.idp.server.platform.exception.ConflictException;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.random.RandomStringGenerator;

/**
 * Drives the four phases of linking an external IdP account.
 *
 * <p>The flow is park-and-claim: the callback, which carries no Bearer token, may only hold the
 * exchanged tokens against the session. Only the Bearer-authenticated complete phase writes a
 * linked account.
 *
 * <p>Configuration is read from the existing {@code federation_configurations} for now. That is a
 * deliberate shortcut for this spike: login and linking want different scopes and different
 * redirect URIs, so they need separate configuration before this ships.
 */
public class AccountLinkingService {

  FederationConfigurationQueryRepository configurationQueryRepository;
  OidcSsoExecutors oidcSsoExecutors;
  AccountLinkingTokenClient tokenClient;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  AccountLinkingSessionQueryRepository sessionQueryRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  LinkedExternalAccountQueryRepository accountQueryRepository;
  AesCipher aesCipher;
  LoggerWrapper log = LoggerWrapper.getLogger(AccountLinkingService.class);

  static final FederationType FEDERATION_TYPE = new FederationType("oidc");
  static final long SESSION_DURATION_SECONDS = 900;
  static final String DEFAULT_ENCRYPTION_KEY_ID = "default";

  public AccountLinkingService(
      FederationConfigurationQueryRepository configurationQueryRepository,
      OidcSsoExecutors oidcSsoExecutors,
      AccountLinkingTokenClient tokenClient,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      LinkedExternalAccountQueryRepository accountQueryRepository,
      AesCipher aesCipher) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.oidcSsoExecutors = oidcSsoExecutors;
    this.tokenClient = tokenClient;
    this.sessionCommandRepository = sessionCommandRepository;
    this.sessionQueryRepository = sessionQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.accountQueryRepository = accountQueryRepository;
    this.aesCipher = aesCipher;
  }

  /**
   * Opens a linking session for the authenticated user and returns where to send the browser.
   *
   * <p>{@code redirectUri} is checked against the calling client's allow list here, while the
   * client is still known. Nothing later in the flow can make that check, and the value is used as
   * a redirect target at the end.
   */
  public AccountLinkingSession start(
      Tenant tenant,
      User user,
      RequestedClientId requestedClientId,
      ExternalIdpProvider provider,
      String redirectUri,
      String requestedScope,
      List<String> allowedRedirectUris) {

    verifyRedirectUri(redirectUri, allowedRedirectUris);

    AccountLinkingPkce pkce = AccountLinkingPkce.generate();
    LocalDateTime now = SystemDateTime.now();

    AccountLinkingSession session =
        new AccountLinkingSession.Builder()
            .state(new AccountLinkingState(new RandomStringGenerator(32).generate()))
            .tenantIdentifier(tenant.identifier())
            .userIdentifier(user.userIdentifier())
            .requestedClientId(requestedClientId)
            .provider(provider)
            .redirectUri(redirectUri)
            .requestedScope(requestedScope)
            .codeVerifier(pkce.codeVerifier())
            .nonce(new RandomStringGenerator(16).generate())
            .status(AccountLinkingSessionStatus.PENDING)
            .expiresAt(now.plusSeconds(SESSION_DURATION_SECONDS))
            .build();

    sessionCommandRepository.register(tenant, session);

    return session;
  }

  /**
   * The URL the RP navigates to, which is on this server rather than the external IdP.
   *
   * <p>Built from the authorization server issuer rather than {@code Tenant#tokenIssuer()}. The
   * latter returns the tenant's {@code domain} column verbatim, which is free-form and need not
   * carry the tenant identifier — a tenant configured as {@code https://api.example.com} would
   * yield {@code https://api.example.com/v1/linking/start}, where {@code v1} is then read as the
   * tenant id. The issuer is the value that identifies the tenant's own base, and is what the other
   * endpoints in the authorization server metadata are built from.
   */
  public String startUri(String issuer, AccountLinkingSession session) {
    return String.format("%s/v1/linking/start?state=%s", issuer, session.state().value());
  }

  /**
   * Verifies the operator and hands back the external authorization URL.
   *
   * <p>This is the only place the second direction of linking CSRF can be stopped: an attacker who
   * lures a victim through the attacker's own session still holds the state afterwards, so the
   * check at complete would pass. Verifying here stops it before the victim ever authenticates at
   * the external IdP.
   */
  public AccountLinkingAuthorization authorize(
      Tenant tenant, AccountLinkingState state, UserIdentifier operator) {
    LocalDateTime now = SystemDateTime.now();
    AccountLinkingSession session = getSession(tenant, state);
    session.verifyNotExpired(now);

    AccountLinkingBrowserBinding browserBinding = AccountLinkingBrowserBinding.generate();
    AccountLinkingSession authorized =
        session.authorize(operator, browserBinding, null, "pwd", now);

    if (!sessionCommandRepository.claim(
        tenant, state, AccountLinkingSessionStatus.PENDING, authorized.status(), now)) {
      throw new AccountLinkingSessionStateException(
          "Account linking session was already started. state=" + state.value());
    }
    sessionCommandRepository.update(tenant, authorized);

    OidcSsoConfiguration configuration = configurationOf(tenant, authorized.provider());

    return new AccountLinkingAuthorization(
        externalAuthorizationUri(configuration, authorized), browserBinding);
  }

  /**
   * Exchanges the authorization code and holds the result against the session.
   *
   * @return where to send the browser next, which is the RP's redirect URI recorded at start
   */
  public String handleCallback(
      Tenant tenant, AccountLinkingState state, String code, String browserBindingSecret) {
    LocalDateTime now = SystemDateTime.now();
    AccountLinkingSession session = getSession(tenant, state);
    session.verifyNotExpired(now);

    // Before anything else, and before the session is claimed: a browser that cannot present the
    // secret issued at /linking/start is not the one that started this link, and its authorization
    // code must not be redeemed. Rejecting here leaves the session usable by the browser that can.
    session.verifyBrowserBinding(browserBindingSecret);

    // Claimed before the exchange: a request that loses here must not redeem the code.
    if (!sessionCommandRepository.claim(
        tenant,
        state,
        AccountLinkingSessionStatus.AUTHORIZED,
        AccountLinkingSessionStatus.PARKED,
        now)) {
      throw new AccountLinkingSessionStateException(
          "Account linking session is not awaiting a callback. state=" + state.value());
    }

    OidcSsoConfiguration configuration = configurationOf(tenant, session.provider());

    OidcTokenResult tokenResult =
        tokenClient.exchangeAuthorizationCode(
            configuration, code, configuration.redirectUri(), session.codeVerifier());

    if (tokenResult.isError()) {
      log.warn(
          "Account linking token exchange failed. provider={}, status={}",
          session.provider().value(),
          tokenResult.statusCode());
      sessionCommandRepository.delete(tenant, state);
      throw new BadRequestException(
          "Account linking failed at the external identity provider token endpoint.");
    }

    User federatedUser = requestUserinfo(configuration, tokenResult);
    ParkedCredentials credentials = encrypt(configuration, tokenResult, federatedUser, now);

    sessionCommandRepository.update(tenant, session.park(credentials));

    return session.redirectUri();
  }

  /** Claims the parked tokens and writes the link. */
  public LinkedExternalAccount complete(
      Tenant tenant, AccountLinkingState state, UserIdentifier operator) {
    LocalDateTime now = SystemDateTime.now();
    AccountLinkingSession session = getSession(tenant, state);
    session.verifyNotExpired(now);

    AccountLinkingSession consumed = session.consume(operator);

    if (!sessionCommandRepository.claim(
        tenant,
        state,
        AccountLinkingSessionStatus.PARKED,
        AccountLinkingSessionStatus.CONSUMED,
        now)) {
      throw new AccountLinkingSessionStateException(
          "Account linking session is not awaiting completion. state=" + state.value());
    }

    LinkedExternalAccount account = store(tenant, consumed, now);
    sessionCommandRepository.delete(tenant, state);

    return account;
  }

  /**
   * Writes the link, either as a new row or as a re-link of one this user already holds.
   *
   * <p>No retry around the unique constraints. On PostgreSQL a failed statement aborts the whole
   * transaction, so a second attempt in the same transaction can only fail with "current
   * transaction is aborted" — catching the violation and re-deriving the alias turns a 409 into a
   * 500 rather than recovering. The rare concurrent-alias case is left to surface as a conflict the
   * caller can retry as a whole request.
   */
  private LinkedExternalAccount store(
      Tenant tenant, AccountLinkingSession session, LocalDateTime now) {
    ParkedCredentials credentials = session.parkedCredentials();

    LinkedExternalAccount own =
        accountQueryRepository.findByUserAndFederatedUser(
            tenant, session.userIdentifier(), session.provider(), credentials.federatedUserId());

    if (own.exists()) {
      LinkedExternalAccount relinked = own.relinkedWith(credentials, now);
      accountCommandRepository.update(tenant, relinked);
      return relinked;
    }

    verifyDuplicateLinkAllowed(tenant, session, credentials);

    LinkedExternalAccount account =
        LinkedExternalAccount.fromConsumedSession(session, nextAlias(tenant, session), now);
    accountCommandRepository.register(tenant, account);
    return account;
  }

  /**
   * Applies the tenant's policy for an external account already linked by someone else.
   *
   * <p>The database does not forbid this. The stored external account identifier is not an identity
   * — nothing authenticates through it — so a blanket constraint would block a shared corporate
   * account and would let whoever links first keep the owner out for good.
   */
  private void verifyDuplicateLinkAllowed(
      Tenant tenant, AccountLinkingSession session, ParkedCredentials credentials) {
    DuplicateLinkPolicy policy =
        linkingConfigurationOf(tenant, session.provider()).duplicateLinkPolicy();

    if (!policy.isReject()) {
      return;
    }

    boolean linkedByAnother =
        accountQueryRepository.existsForOtherUser(
            tenant, session.userIdentifier(), session.provider(), credentials.federatedUserId());

    if (linkedByAnother) {
      // Deliberately vague: naming the current owner would turn this into an enumeration oracle.
      throw new ConflictException("This external account cannot be linked.");
    }
  }

  private AccountAlias nextAlias(Tenant tenant, AccountLinkingSession session) {
    if (session.hasAccountAlias()) {
      return session.accountAlias();
    }
    int linked =
        accountQueryRepository.countByProvider(
            tenant, session.userIdentifier(), session.provider());
    return AccountAlias.of(session.provider(), linked + 1);
  }

  private ParkedCredentials encrypt(
      OidcSsoConfiguration configuration,
      OidcTokenResult tokenResult,
      User federatedUser,
      LocalDateTime now) {

    EncryptedData accessToken = aesCipher.encrypt(tokenResult.accessToken());
    EncryptedData refreshToken =
        tokenResult.hasRefreshToken() ? aesCipher.encrypt(tokenResult.refreshToken()) : null;

    String grantedScope =
        tokenResult.hasScope() ? tokenResult.scope() : configuration.scopeAsString();

    return new ParkedCredentials(
        federatedUser.externalUserId(),
        federatedUser.email(),
        grantedScope,
        accessToken,
        refreshToken,
        DEFAULT_ENCRYPTION_KEY_ID,
        now.plusSeconds(tokenResult.expiresIn()),
        refreshToken == null ? null : now.plusSeconds(configuration.refreshTokenExpiresIn()));
  }

  private User requestUserinfo(OidcSsoConfiguration configuration, OidcTokenResult tokenResult) {
    OidcSsoExecutor executor = oidcSsoExecutors.get(configuration.ssoProvider());
    UserinfoExecutionResult result =
        executor.requestUserInfo(
            new OidcUserinfoRequest(
                configuration.userinfoEndpoint(),
                tokenResult.accessToken(),
                configuration.userinfoExecution()));

    if (result.isError()) {
      throw new BadRequestException(
          "Account linking failed at the external identity provider userinfo endpoint.");
    }

    return new UserInfoMapper(
            configuration.userinfoMappingRules(), result.contents(), configuration.issuerName())
        .toUser();
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

  private AccountLinkingConfiguration linkingConfigurationOf(
      Tenant tenant, ExternalIdpProvider provider) {
    return configurationQueryRepository.get(
        tenant,
        FEDERATION_TYPE,
        new SsoProvider(provider.value()),
        AccountLinkingConfiguration.class);
  }

  private OidcSsoConfiguration configurationOf(Tenant tenant, ExternalIdpProvider provider) {
    return configurationQueryRepository.get(
        tenant, FEDERATION_TYPE, new SsoProvider(provider.value()), OidcSsoConfiguration.class);
  }

  private AccountLinkingSession getSession(Tenant tenant, AccountLinkingState state) {
    AccountLinkingSession session = sessionQueryRepository.find(tenant, state);
    if (!session.exists()) {
      throw new NotFoundException("Account linking session not found.");
    }
    return session;
  }

  private void verifyRedirectUri(String redirectUri, List<String> allowedRedirectUris) {
    if (redirectUri == null || redirectUri.isEmpty()) {
      throw new BadRequestException("redirect_uri is required.");
    }
    if (!allowedRedirectUris.contains(redirectUri)) {
      throw new BadRequestException("redirect_uri is not registered for this client.");
    }
  }
}
