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
import java.util.HashMap;
import java.util.Map;
import org.idp.server.account_linking.AccountLinkingConfigurationResolver;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.account_linking.gateway.ExternalIdpTokenGateway;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenParameters;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenRequest;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenResult;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.federation.sso.oidc.OidcTokenResult;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Hands the stored access token of one linked external account to a client.
 *
 * <p>The client authenticates, and names the user it acts for with that user's access token. The
 * token is released only when every check holds: the client is confidential, the user's token was
 * issued to that same client and carries {@code linked_account:read_token}, and the client is
 * allowed the account's provider. Only the access token leaves; the refresh token stays here and is
 * spent here, because an application that spent it would rotate the stored one out from under this
 * server.
 */
public class LinkedExternalAccountTokenHandler {

  /** The scope a user grants for a client to read their linked accounts' tokens. */
  public static final String READ_TOKEN_SCOPE = "linked_account:read_token";

  /**
   * How close to expiry an access token is refreshed before it is handed out.
   *
   * <p>A token released seconds before it expires fails at the external API the client calls next.
   */
  static final long REFRESH_MARGIN_SECONDS = 30;

  static final String ISSUED_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  ClientAuthenticationHandler clientAuthenticationHandler;
  UserQueryRepository userQueryRepository;
  LinkedExternalAccountQueryRepository accountQueryRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  AccountLinkingConfigurationResolver configurationResolver;
  ExternalIdpTokenGateway tokenGateway;
  AesCipher aesCipher;
  LoggerWrapper log = LoggerWrapper.getLogger(LinkedExternalAccountTokenHandler.class);

  public LinkedExternalAccountTokenHandler(
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      ClientAuthenticationHandler clientAuthenticationHandler,
      UserQueryRepository userQueryRepository,
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      AccountLinkingConfigurationResolver configurationResolver,
      ExternalIdpTokenGateway tokenGateway,
      AesCipher aesCipher) {
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.clientAuthenticationHandler = clientAuthenticationHandler;
    this.userQueryRepository = userQueryRepository;
    this.accountQueryRepository = accountQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.configurationResolver = configurationResolver;
    this.tokenGateway = tokenGateway;
    this.aesCipher = aesCipher;
  }

  public LinkedExternalAccountTokenResult handle(LinkedExternalAccountTokenRequest request) {

    Tenant tenant = request.tenant();
    RequestedClientId requestedClientId = request.clientId();
    User user = new User();

    try {
      ClientConfiguration client = authenticateClient(request, requestedClientId);
      OAuthToken userToken = verifyUserToken(tenant, request.parameters(), client);
      user = activeUser(tenant, userToken);

      LinkedExternalAccount account =
          accountQueryRepository.find(tenant, user.userIdentifier(), request.alias());
      if (!account.exists()) {
        throw LinkedExternalAccountTokenException.notFound("Linked external account not found.");
      }
      verifyProviderAllowed(client, account);

      LocalDateTime now = SystemDateTime.now();
      if (isUsable(account, now)) {
        return retrieved(account, false, requestedClientId, user);
      }

      LinkedExternalAccount refreshed = refreshUnderLock(tenant, user.userIdentifier(), request);
      return retrieved(refreshed, true, requestedClientId, user);

    } catch (LinkedExternalAccountTokenException exception) {
      return refused(exception, requestedClientId, user);
    }
  }

  private ClientConfiguration authenticateClient(
      LinkedExternalAccountTokenRequest request, RequestedClientId requestedClientId) {
    Tenant tenant = request.tenant();
    try {
      AuthorizationServerConfiguration serverConfiguration =
          authorizationServerConfigurationQueryRepository.get(tenant);
      ClientConfiguration clientConfiguration =
          clientConfigurationQueryRepository.get(tenant, requestedClientId);

      // The shared handler lets token_endpoint_auth_method=none through unchecked; a public client
      // cannot keep a stored token from the user agent it runs in, so it is refused here first.
      if (clientConfiguration.clientAuthenticationType().isNone()) {
        throw LinkedExternalAccountTokenException.invalidClient(
            "Public clients may not retrieve stored external tokens.");
      }

      clientAuthenticationHandler.authenticate(
          new LinkedExternalAccountTokenRequestContext(
              request, requestedClientId, serverConfiguration, clientConfiguration));
      return clientConfiguration;

    } catch (ClientConfigurationNotFoundException | ClientUnAuthorizedException exception) {
      throw LinkedExternalAccountTokenException.invalidClient("Client authentication failed.");
    }
  }

  private OAuthToken verifyUserToken(
      Tenant tenant, LinkedExternalAccountTokenParameters parameters, ClientConfiguration client) {

    if (!parameters.hasUserAccessToken()) {
      throw LinkedExternalAccountTokenException.invalidRequest("token is required.");
    }

    OAuthToken userToken = oAuthTokenQueryRepository.find(tenant, parameters.userAccessToken());
    if (!userToken.exists() || userToken.isExpiredAccessToken(SystemDateTime.now())) {
      throw LinkedExternalAccountTokenException.invalidToken("token is invalid or expired.");
    }
    if (userToken.isClientCredentialsGrant() || !userToken.hasSubject()) {
      throw LinkedExternalAccountTokenException.invalidToken("token does not name a user.");
    }

    // A sender-constrained token is only worth something if the proof is checked. Until this
    // endpoint verifies DPoP proofs and certificate thumbprints, it refuses such tokens instead of
    // accepting them as if they were bearer tokens.
    if (userToken.accessToken().hasDPoPBinding()
        || userToken.accessToken().hasClientCertification()
        || userToken.accessToken().hasClientInstanceBinding()) {
      throw LinkedExternalAccountTokenException.invalidRequest(
          "Sender-constrained user tokens are not supported by this endpoint yet.");
    }

    // The client presenting the user's token must be the one it was issued to. Otherwise any
    // confidential client holding a leaked user token could read that user's external tokens.
    if (!client.isIdentifiedBy(userToken.requestedClientId().value())) {
      throw LinkedExternalAccountTokenException.unauthorizedClient(
          "token was not issued to the authenticated client.");
    }

    if (!userToken.scopeAsList().contains(READ_TOKEN_SCOPE)) {
      throw LinkedExternalAccountTokenException.insufficientScope(
          "token does not carry " + READ_TOKEN_SCOPE + ".");
    }

    return userToken;
  }

  private User activeUser(Tenant tenant, OAuthToken userToken) {
    User user =
        userQueryRepository.findById(tenant, new UserIdentifier(userToken.subject().value()));
    if (!user.isActive()) {
      throw LinkedExternalAccountTokenException.invalidToken("The user is not active.");
    }
    return user;
  }

  private void verifyProviderAllowed(ClientConfiguration client, LinkedExternalAccount account) {
    if (!client.linkingTokenProviders().contains(account.provider().value())) {
      throw LinkedExternalAccountTokenException.unauthorizedClient(
          "The client is not allowed to retrieve tokens of this provider.");
    }
  }

  private boolean isUsable(LinkedExternalAccount account, LocalDateTime now) {
    return !account.isAccessTokenExpired(now.plusSeconds(REFRESH_MARGIN_SECONDS));
  }

  /**
   * Refreshes the stored token with the row locked.
   *
   * <p>Concurrent requests for the same account wait here, and the ones that get the lock after a
   * refresh find a usable token and return it. Without the lock both would spend the same refresh
   * token, and a provider that rotates refresh tokens would revoke the grant for the loser.
   */
  private LinkedExternalAccount refreshUnderLock(
      Tenant tenant, UserIdentifier userIdentifier, LinkedExternalAccountTokenRequest request) {

    LinkedExternalAccount locked =
        accountQueryRepository.lock(tenant, userIdentifier, request.alias());
    LocalDateTime now = SystemDateTime.now();
    if (isUsable(locked, now)) {
      return locked;
    }

    if (!locked.hasRefreshToken() || locked.isRefreshTokenExpired(now)) {
      throw LinkedExternalAccountTokenException.relinkRequired(
          "The stored token has expired and cannot be refreshed. Link the account again.");
    }

    OidcSsoConfiguration configuration =
        configurationResolver.resolve(tenant, locked.provider()).oidc();
    OidcTokenResult tokenResult =
        tokenGateway.refresh(configuration, aesCipher.decrypt(locked.encryptedRefreshToken()));

    if (tokenResult.isError() || !tokenResult.hasAccessToken()) {
      throw refreshFailure(locked, tokenResult);
    }

    LinkedExternalAccount refreshed = withRefreshedTokens(locked, configuration, tokenResult, now);
    accountCommandRepository.update(tenant, refreshed);
    return refreshed;
  }

  private LinkedExternalAccountTokenException refreshFailure(
      LinkedExternalAccount account, OidcTokenResult tokenResult) {
    log.warn(
        "Stored external token refresh failed. provider={}, alias={}, status={}",
        account.provider().value(),
        account.accountAlias().value(),
        tokenResult.statusCode());

    // A 5xx or a network failure says nothing about the grant; only a 4xx from the provider means
    // the refresh token itself is no longer accepted.
    if (tokenResult.statusCode() >= 500) {
      return LinkedExternalAccountTokenException.externalIdpUnavailable(
          "The external identity provider could not refresh the token. Try again later.");
    }
    return LinkedExternalAccountTokenException.relinkRequired(
        "The external identity provider rejected the stored refresh token. Link the account again.");
  }

  private LinkedExternalAccount withRefreshedTokens(
      LinkedExternalAccount account,
      OidcSsoConfiguration configuration,
      OidcTokenResult tokenResult,
      LocalDateTime now) {

    EncryptedData accessToken = aesCipher.encrypt(tokenResult.accessToken());
    LocalDateTime accessTokenExpiresAt =
        tokenResult.hasExpiresIn() ? now.plusSeconds(tokenResult.expiresIn()) : null;

    // A provider that does not rotate answers without a refresh token; the stored one stays valid.
    if (!tokenResult.hasRefreshToken()) {
      return account.withRefreshedTokens(
          accessToken,
          account.encryptedRefreshToken(),
          accessTokenExpiresAt,
          account.refreshTokenExpiresAt(),
          now);
    }

    return account.withRefreshedTokens(
        accessToken,
        aesCipher.encrypt(tokenResult.refreshToken()),
        accessTokenExpiresAt,
        now.plusSeconds(configuration.refreshTokenExpiresIn()),
        now);
  }

  private LinkedExternalAccountTokenResult retrieved(
      LinkedExternalAccount account,
      boolean refreshed,
      RequestedClientId requestedClientId,
      User user) {

    Map<String, Object> contents = new HashMap<>();
    contents.put("access_token", aesCipher.decrypt(account.encryptedAccessToken()));
    contents.put("token_type", "Bearer");
    contents.put("issued_token_type", ISSUED_TOKEN_TYPE);
    if (account.accessTokenExpiresAt() != null) {
      long expiresIn =
          java.time.Duration.between(SystemDateTime.now(), account.accessTokenExpiresAt())
              .getSeconds();
      contents.put("expires_in", Math.max(expiresIn, 0));
    }
    if (account.scope() != null && !account.scope().isEmpty()) {
      contents.put("scope", account.scope());
    }

    AccountLinkingResult result =
        AccountLinkingResult.success(
                AccountLinkingStatus.OK,
                contents,
                DefaultSecurityEventType.external_account_token_retrieved,
                user)
            .withContext(requestedClientId, user);
    return new LinkedExternalAccountTokenResult(result, refreshed, false);
  }

  private LinkedExternalAccountTokenResult refused(
      LinkedExternalAccountTokenException exception,
      RequestedClientId requestedClientId,
      User user) {
    log.warn("Stored external token retrieval refused: {}", exception.getMessage());

    AccountLinkingResult result =
        AccountLinkingResult.error(
                exception.status(),
                exception.error(),
                exception.getMessage(),
                DefaultSecurityEventType.external_account_token_retrieval_failed)
            .withContext(requestedClientId, user);
    return new LinkedExternalAccountTokenResult(result, false, exception.isRelinkRequired());
  }
}
