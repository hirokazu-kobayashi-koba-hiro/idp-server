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

package org.idp.server.core.openid.token.handler.tokenintrospection;

import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.TokenUserFindingDelegate;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionExtensionRequest;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionRequestContext;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.core.openid.token.tokenintrospection.validator.TokenIntrospectionValidator;
import org.idp.server.core.openid.token.tokenintrospection.verifier.TokenIntrospectionExtensionVerifier;
import org.idp.server.core.openid.token.tokenintrospection.verifier.TokenIntrospectionUserVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenIntrospectionExtensionHandler {

  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  ClientAuthenticationHandler clientAuthenticationHandler;

  public TokenIntrospectionExtensionHandler(
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.clientAuthenticationHandler = new ClientAuthenticationHandler();
  }

  public TokenIntrospectionResponse handle(
      TokenIntrospectionExtensionRequest request, TokenUserFindingDelegate delegate) {
    TokenIntrospectionValidator validator = new TokenIntrospectionValidator(request.toParameters());
    validator.validate();

    Tenant tenant = request.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, request.clientId());

    TokenIntrospectionRequestContext introspectionRequestContext =
        new TokenIntrospectionRequestContext(
            request.clientSecretBasic(),
            request.clientCertFormMtls(),
            request.toParameters(),
            authorizationServerConfiguration,
            clientConfiguration);
    clientAuthenticationHandler.authenticate(introspectionRequestContext);

    OAuthToken oAuthToken = find(request);
    TokenIntrospectionExtensionVerifier verifier =
        new TokenIntrospectionExtensionVerifier(
            request.clientCertForTokenBinding(),
            request.scopes(),
            oAuthToken,
            authorizationServerConfiguration,
            clientConfiguration);
    verifier.verify();

    if (!oAuthToken.isClientCredentialsGrant()) {
      User user = delegate.findUser(tenant, oAuthToken.subject());
      TokenIntrospectionUserVerifier userVerifier = new TokenIntrospectionUserVerifier(user);
      userVerifier.verify();
    }

    Map<String, Object> contents =
        TokenIntrospectionContentsCreator.createSuccessContents(oAuthToken);

    if (oAuthToken.isOneshotToken()) {
      oAuthTokenCommandRepository.delete(request.tenant(), oAuthToken);
    }

    return new TokenIntrospectionResponse(TokenIntrospectionRequestStatus.OK, oAuthToken, contents);
  }

  OAuthToken find(TokenIntrospectionExtensionRequest request) {
    TokenIntrospectionRequestParameters parameters = request.toParameters();
    AccessTokenEntity accessTokenEntity = parameters.accessToken();
    Tenant tenant = request.tenant();
    OAuthToken oAuthToken = oAuthTokenQueryRepository.find(tenant, accessTokenEntity);
    if (oAuthToken.exists()) {
      return oAuthToken;
    } else {
      RefreshTokenEntity refreshTokenEntity = parameters.refreshToken();
      return oAuthTokenQueryRepository.find(tenant, refreshTokenEntity);
    }
  }
}
