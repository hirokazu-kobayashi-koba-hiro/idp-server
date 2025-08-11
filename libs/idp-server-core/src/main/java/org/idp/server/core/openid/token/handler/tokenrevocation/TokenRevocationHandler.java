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

package org.idp.server.core.openid.token.handler.tokenrevocation;

import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.tokenrevocation.TokenRevocationRequestContext;
import org.idp.server.core.openid.token.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.core.openid.token.tokenrevocation.validator.TokenRevocationValidator;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenRevocationHandler {
  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  ClientAuthenticationHandler clientAuthenticationHandler;

  public TokenRevocationHandler(
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

  public TokenRevocationResponse handle(TokenRevocationRequest request) {
    TokenRevocationValidator validator = new TokenRevocationValidator(request.toParameters());
    validator.validate();

    Tenant tenant = request.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, request.clientId());
    TokenRevocationRequestContext tokenRevocationRequestContext =
        new TokenRevocationRequestContext(
            request.clientSecretBasic(),
            request.toClientCert(),
            request.toParameters(),
            authorizationServerConfiguration,
            clientConfiguration);
    clientAuthenticationHandler.authenticate(tokenRevocationRequestContext);

    OAuthToken oAuthToken = find(request);
    if (oAuthToken.exists()) {
      oAuthTokenCommandRepository.delete(tenant, oAuthToken);
    }
    return new TokenRevocationResponse(TokenRevocationRequestStatus.OK, oAuthToken, Map.of());
  }

  // TODO consider, because duplicated method token introspection handler
  OAuthToken find(TokenRevocationRequest request) {
    TokenRevocationRequestParameters parameters = request.toParameters();
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
