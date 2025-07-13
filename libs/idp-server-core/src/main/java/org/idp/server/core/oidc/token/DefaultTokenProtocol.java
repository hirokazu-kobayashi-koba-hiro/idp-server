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

package org.idp.server.core.oidc.token;

import java.util.Map;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.token.handler.token.TokenRequestErrorHandler;
import org.idp.server.core.oidc.token.handler.token.TokenRequestHandler;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequest;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.oidc.token.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oidc.token.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.oidc.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.oidc.token.service.OAuthTokenCreationService;
import org.idp.server.core.oidc.token.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.oidc.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.core.oidc.type.oauth.GrantType;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.log.LoggerWrapper;

public class DefaultTokenProtocol implements TokenProtocol {

  TokenRequestHandler tokenRequestHandler;
  TokenIntrospectionHandler tokenIntrospectionHandler;
  TokenRevocationHandler tokenRevocationHandler;
  TokenRequestErrorHandler errorHandler;
  PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate;
  LoggerWrapper log = LoggerWrapper.getLogger(DefaultTokenProtocol.class);

  public DefaultTokenProtocol(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate,
      Map<GrantType, OAuthTokenCreationService> extensionOAuthTokenCreationServices) {
    this.tokenRequestHandler =
        new TokenRequestHandler(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            authorizationGrantedRepository,
            oAuthTokenCommandRepository,
            oAuthTokenQueryRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository,
            extensionOAuthTokenCreationServices);
    this.errorHandler = new TokenRequestErrorHandler();
    this.tokenIntrospectionHandler =
        new TokenIntrospectionHandler(oAuthTokenCommandRepository, oAuthTokenQueryRepository);
    this.tokenRevocationHandler =
        new TokenRevocationHandler(
            oAuthTokenCommandRepository,
            oAuthTokenQueryRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.passwordCredentialsGrantDelegate = passwordCredentialsGrantDelegate;
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {
      return tokenRequestHandler.handle(tokenRequest, passwordCredentialsGrantDelegate);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public TokenIntrospectionResponse inspect(TokenIntrospectionRequest request) {
    try {

      return tokenIntrospectionHandler.handle(request);
    } catch (TokenInvalidException exception) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(
          TokenIntrospectionRequestStatus.INVALID_TOKEN, contents);
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(TokenIntrospectionRequestStatus.SERVER_ERROR, contents);
    }
  }

  public TokenRevocationResponse revoke(TokenRevocationRequest request) {
    try {

      return tokenRevocationHandler.handle(request);
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      return new TokenRevocationResponse(
          TokenRevocationRequestStatus.SERVER_ERROR, new OAuthToken(), Map.of());
    }
  }
}
