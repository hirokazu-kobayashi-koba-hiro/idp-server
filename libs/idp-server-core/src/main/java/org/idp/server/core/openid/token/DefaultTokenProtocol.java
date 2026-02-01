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

package org.idp.server.core.openid.token;

import java.util.Map;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.token.handler.token.TokenRequestErrorHandler;
import org.idp.server.core.openid.token.handler.token.TokenRequestHandler;
import org.idp.server.core.openid.token.handler.token.io.TokenRequest;
import org.idp.server.core.openid.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.openid.token.handler.tokenintrospection.*;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.*;
import org.idp.server.core.openid.token.handler.tokenrevocation.TokenRevocationErrorHandler;
import org.idp.server.core.openid.token.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.service.OAuthTokenCreationService;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenUserInactiveException;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.log.LoggerWrapper;

public class DefaultTokenProtocol implements TokenProtocol {

  TokenRequestHandler tokenRequestHandler;
  TokenIntrospectionHandler introspectionHandler;
  TokenIntrospectionExtensionHandler introspectionExtensionHandler;
  TokenIntrospectionInternalHandler introspectionInternalHandler;
  TokenRevocationHandler revocationHandler;
  TokenRequestErrorHandler errorHandler;
  TokenIntrospectionErrorHandler introspectionErrorHandler;
  TokenRevocationErrorHandler revocationErrorHandler;
  PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate;
  LoggerWrapper log = LoggerWrapper.getLogger(DefaultTokenProtocol.class);

  public DefaultTokenProtocol(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate,
      Map<GrantType, OAuthTokenCreationService> extensionOAuthTokenCreationServices) {
    this.tokenRequestHandler =
        new TokenRequestHandler(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            oAuthTokenCommandRepository,
            oAuthTokenQueryRepository,
            authorizationGrantedRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository,
            extensionOAuthTokenCreationServices);
    this.errorHandler = new TokenRequestErrorHandler();
    this.introspectionHandler =
        new TokenIntrospectionHandler(
            oAuthTokenCommandRepository,
            oAuthTokenQueryRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.introspectionHandler =
        new TokenIntrospectionHandler(
            oAuthTokenCommandRepository,
            oAuthTokenQueryRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.introspectionExtensionHandler =
        new TokenIntrospectionExtensionHandler(
            oAuthTokenCommandRepository,
            oAuthTokenQueryRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.introspectionInternalHandler =
        new TokenIntrospectionInternalHandler(
            oAuthTokenCommandRepository, oAuthTokenQueryRepository);
    this.introspectionErrorHandler = new TokenIntrospectionErrorHandler();
    this.revocationHandler =
        new TokenRevocationHandler(
            oAuthTokenCommandRepository,
            oAuthTokenQueryRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.revocationErrorHandler = new TokenRevocationErrorHandler();
    this.passwordCredentialsGrantDelegate = passwordCredentialsGrantDelegate;
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  public TokenRequestResponse request(
      TokenRequest tokenRequest, TokenUserFindingDelegate tokenUserFindingDelegate) {
    try {
      return tokenRequestHandler.handle(
          tokenRequest, passwordCredentialsGrantDelegate, tokenUserFindingDelegate);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public TokenIntrospectionResponse inspect(
      TokenIntrospectionRequest request, TokenUserFindingDelegate delegate) {
    try {

      return introspectionHandler.handle(request, delegate);
    } catch (TokenInvalidException exception) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(
          TokenIntrospectionRequestStatus.INVALID_TOKEN, contents);
    } catch (TokenUserInactiveException exception) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(
          TokenIntrospectionRequestStatus.INACTIVE_USER, contents);
    } catch (Exception exception) {

      return introspectionErrorHandler.handle(exception);
    }
  }

  public TokenIntrospectionResponse inspectWithVerification(
      TokenIntrospectionExtensionRequest request, TokenUserFindingDelegate delegate) {
    try {

      return introspectionExtensionHandler.handle(request, delegate);
    } catch (Exception exception) {

      return introspectionErrorHandler.handle(exception);
    }
  }

  public TokenIntrospectionResponse inspectForInternal(TokenIntrospectionInternalRequest request) {
    try {

      return introspectionInternalHandler.handle(request);
    } catch (TokenInvalidException exception) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(
          TokenIntrospectionRequestStatus.INVALID_TOKEN, contents);
    } catch (Exception exception) {

      return introspectionErrorHandler.handle(exception);
    }
  }

  public TokenRevocationResponse revoke(TokenRevocationRequest request) {
    try {

      return revocationHandler.handle(request);
    } catch (Exception exception) {

      return revocationErrorHandler.handle(exception);
    }
  }
}
