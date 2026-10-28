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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.account_linking.ExternalAccessToken;
import org.idp.server.account_linking.ExternalAccessTokenAcquisition;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.LinkedExternalAccountTokenService;
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenRequest;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenResult;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.account_linking.validator.LinkedExternalAccountTokenRequestValidator;
import org.idp.server.account_linking.verifier.LinkedExternalAccountTokenBindingVerifier;
import org.idp.server.account_linking.verifier.LinkedExternalAccountTokenVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationHeaderValidator;
import org.idp.server.core.openid.oauth.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifier;
import org.idp.server.core.openid.oauth.replay.JwtReplayDetector;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.tokenintrospection.verifier.DPoPBindingVerifier;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Hands the stored access token of one linked external account to a client.
 *
 * <p>The client authenticates, and names the user it acts for with that user's access token. The
 * token is released only when every check holds: the client authenticates (not {@code none}), the
 * user's token was issued to that same client, is presented by the sender it is bound to, and
 * carries {@code linked_account:read_token}, and the client is allowed the account's provider. What
 * to hand out once those hold — refreshing or re-encrypting the stored token first — is {@link
 * LinkedExternalAccountTokenService}'s.
 */
public class LinkedExternalAccountTokenHandler {

  static final String ISSUED_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  ClientAuthenticationHandler clientAuthenticationHandler;
  UserQueryRepository userQueryRepository;
  LinkedExternalAccountQueryRepository accountQueryRepository;
  LinkedExternalAccountTokenService tokenService;
  LinkedExternalAccountTokenRequestValidator validator;
  LinkedExternalAccountTokenVerifier verifier;
  JwtReplayDetector replayDetector;
  LoggerWrapper log = LoggerWrapper.getLogger(LinkedExternalAccountTokenHandler.class);

  public LinkedExternalAccountTokenHandler(
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      ClientAuthenticationHandler clientAuthenticationHandler,
      UserQueryRepository userQueryRepository,
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountTokenService tokenService,
      JwtReplayDetector replayDetector) {
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.clientAuthenticationHandler = clientAuthenticationHandler;
    this.userQueryRepository = userQueryRepository;
    this.accountQueryRepository = accountQueryRepository;
    this.tokenService = tokenService;
    this.replayDetector = replayDetector;
    this.validator = new LinkedExternalAccountTokenRequestValidator();
    this.verifier = new LinkedExternalAccountTokenVerifier();
  }

  public LinkedExternalAccountTokenResult handle(LinkedExternalAccountTokenRequest request) {

    Tenant tenant = request.tenant();
    RequestedClientId requestedClientId = request.clientId();
    User user = new User();
    ExternalIdpProvider provider = null;

    try {
      AuthorizationServerConfiguration serverConfiguration =
          authorizationServerConfigurationQueryRepository.get(tenant);
      AuthenticatedClient authenticated =
          authenticateClient(request, requestedClientId, serverConfiguration);
      ClientConfiguration client = authenticated.configuration();
      validator.validate(request.parameters());

      OAuthToken userToken =
          oAuthTokenQueryRepository.find(tenant, request.parameters().userAccessToken());
      verifier.verifyUserToken(userToken, client, SystemDateTime.now());
      bindingVerifier(tenant, serverConfiguration)
          .verify(userToken, request, authenticated.credentials());

      user = userQueryRepository.findById(tenant, new UserIdentifier(userToken.subject().value()));
      verifier.verifyUserIsActive(user);

      LinkedExternalAccount account =
          accountQueryRepository.find(tenant, user.userIdentifier(), request.alias());
      if (!account.exists()) {
        throw LinkedExternalAccountTokenException.notFound("Linked external account not found.");
      }
      provider = account.provider();
      verifier.verifyProviderInLinkingTokenProviders(client, account);

      ExternalAccessTokenAcquisition acquisition = tokenService.acquire(tenant, account);
      return retrieved(acquisition, provider, requestedClientId, user);

    } catch (LinkedExternalAccountTokenException exception) {
      return refused(exception, requestedClientId, user, provider);
    }
  }

  private AuthenticatedClient authenticateClient(
      LinkedExternalAccountTokenRequest request,
      RequestedClientId requestedClientId,
      AuthorizationServerConfiguration serverConfiguration) {
    Tenant tenant = request.tenant();
    try {
      new ClientAttestationHeaderValidator(
              request.clientAttestationHeaders(), request.clientAttestationPopHeaders())
          .validate();
      ClientConfiguration clientConfiguration =
          clientConfigurationQueryRepository.get(tenant, requestedClientId);

      // The shared handler lets token_endpoint_auth_method=none through unchecked; a public client
      // cannot keep a stored token from the user agent it runs in, so it is refused here first.
      if (clientConfiguration.clientAuthenticationType().isNone()) {
        throw LinkedExternalAccountTokenException.invalidClient(
            "Public clients may not retrieve stored external tokens.");
      }

      ClientCredentials credentials =
          clientAuthenticationHandler.authenticate(
              new LinkedExternalAccountTokenRequestContext(
                  request, requestedClientId, serverConfiguration, clientConfiguration));
      return new AuthenticatedClient(clientConfiguration, credentials);

    } catch (ClientConfigurationNotFoundException | ClientUnAuthorizedException exception) {
      throw LinkedExternalAccountTokenException.invalidClient("Client authentication failed.");
    }
  }

  /** The client as configured, and what its authentication on this request established. */
  record AuthenticatedClient(ClientConfiguration configuration, ClientCredentials credentials) {}

  /** DPoP proofs are checked against the tenant's window and replay cache, as at {@code /me}. */
  private LinkedExternalAccountTokenBindingVerifier bindingVerifier(
      Tenant tenant, AuthorizationServerConfiguration serverConfiguration) {
    return new LinkedExternalAccountTokenBindingVerifier(
        new DPoPBindingVerifier(
            new DPoPProofVerifier(
                tenant, serverConfiguration.dpopProofAcceptableWindow(), replayDetector)));
  }

  private LinkedExternalAccountTokenResult retrieved(
      ExternalAccessTokenAcquisition acquisition,
      ExternalIdpProvider provider,
      RequestedClientId requestedClientId,
      User user) {

    ExternalAccessToken accessToken = acquisition.accessToken();
    Map<String, Object> contents = new HashMap<>();
    contents.put("access_token", accessToken.value());
    contents.put("token_type", "Bearer");
    contents.put("issued_token_type", ISSUED_TOKEN_TYPE);
    if (accessToken.hasExpiresAt()) {
      contents.put("expires_in", accessToken.expiresIn(SystemDateTime.now()));
    }
    if (accessToken.hasScope()) {
      contents.put("scope", accessToken.scope());
    }

    AccountLinkingResult result =
        AccountLinkingResult.success(
                AccountLinkingStatus.OK,
                contents,
                DefaultSecurityEventType.external_account_token_retrieved,
                user)
            .withContext(requestedClientId, user);
    return new LinkedExternalAccountTokenResult(
        result, acquisition.isRefreshed(), false, provider, null);
  }

  private LinkedExternalAccountTokenResult refused(
      LinkedExternalAccountTokenException exception,
      RequestedClientId requestedClientId,
      User user,
      ExternalIdpProvider provider) {
    log.warn("Stored external token retrieval refused: {}", exception.getMessage());

    AccountLinkingResult result =
        AccountLinkingResult.error(
                exception.status(),
                exception.error(),
                exception.getMessage(),
                DefaultSecurityEventType.external_account_token_retrieval_failed)
            .withContext(requestedClientId, user);
    return new LinkedExternalAccountTokenResult(
        result, false, exception.isRelinkRequired(), provider, exception.error());
  }
}
