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

package org.idp.server.core.openid.oauth;

import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.factory.RequestObjectFactories;
import org.idp.server.core.openid.oauth.gateway.RequestObjectHttpClient;
import org.idp.server.core.openid.oauth.handler.*;
import org.idp.server.core.openid.oauth.io.*;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.response.AuthorizationResponse;
import org.idp.server.core.openid.session.OIDCSessionCoordinator;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** OAuthApi */
public class DefaultOAuthProtocol implements OAuthProtocol {
  OAuthRequestHandler requestHandler;
  OAuthRequestErrorHandler oAuthRequestErrorHandler;
  OAuthAuthorizeHandler authorizeHandler;
  OAuthAuthorizeErrorHandler authAuthorizeErrorHandler;
  OAuthDenyHandler oAuthDenyHandler;
  OAuthDenyErrorHandler denyErrorHandler;
  OAuthHandler oAuthHandler;
  OAuthLogoutErrorHandler logoutErrorHandler;

  public DefaultOAuthProtocol(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      OIDCSessionCoordinator oidcSessionCoordinator,
      HttpRequestExecutor httpRequestExecutor) {
    this.requestHandler =
        new OAuthRequestHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository,
            new RequestObjectHttpClient(httpRequestExecutor),
            new RequestObjectFactories(),
            authorizationGrantedRepository);
    this.authorizeHandler =
        new OAuthAuthorizeHandler(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            oAuthTokenCommandRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.oAuthDenyHandler =
        new OAuthDenyHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.oAuthHandler =
        new OAuthHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository,
            oidcSessionCoordinator);
    this.oAuthRequestErrorHandler = new OAuthRequestErrorHandler();
    this.authAuthorizeErrorHandler = new OAuthAuthorizeErrorHandler();
    this.denyErrorHandler = new OAuthDenyErrorHandler();
    this.logoutErrorHandler = new OAuthLogoutErrorHandler();
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  @Override
  public OAuthPushedRequestResponse push(OAuthPushedRequest oAuthPushedRequest) {
    try {

      OAuthPushedRequestContext context = requestHandler.handlePushedRequest(oAuthPushedRequest);

      return context.createResponse();
    } catch (Exception exception) {

      return oAuthRequestErrorHandler.handlePushedRequest(exception);
    }
  }

  /**
   * request
   *
   * <p>The authorization endpoint is used to interact with the resource owner and obtain an
   * authorization grant. The authorization server MUST first verify the identity of the resource
   * owner. The way in which the authorization server authenticates the resource owner (e.g.,
   * username and password login, session cookies) is beyond the scope of this specification.
   */
  public OAuthRequestResponse request(OAuthRequest oAuthRequest) {

    try {

      OAuthRequestContext context = requestHandler.handleRequest(oAuthRequest);

      if (context.canAutomaticallyAuthorize()) {

        OAuthAuthorizeRequest oAuthAuthorizeRequest = context.createOAuthAuthorizeRequest();
        AuthorizationResponse response = authorizeHandler.handle(oAuthAuthorizeRequest);
        return new OAuthRequestResponse(OAuthRequestStatus.NO_INTERACTION_OK, context, response);
      }

      return context.createResponse();
    } catch (Exception exception) {

      return oAuthRequestErrorHandler.handle(exception);
    }
  }

  public OAuthViewDataResponse getViewData(OAuthViewDataRequest request) {

    return oAuthHandler.handleViewData(request);
  }

  public AuthorizationRequest get(Tenant tenant, AuthorizationRequestIdentifier identifier) {

    return oAuthHandler.handleGettingData(tenant, identifier);
  }

  public OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request) {
    try {

      AuthorizationResponse response = authorizeHandler.handle(request);

      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.OK, response);
    } catch (Exception exception) {

      return authAuthorizeErrorHandler.handle(exception);
    }
  }

  public OAuthDenyResponse deny(OAuthDenyRequest request) {
    try {

      return oAuthDenyHandler.handle(request);
    } catch (Exception exception) {

      return denyErrorHandler.handle(exception);
    }
  }

  public OAuthLogoutResponse logout(OAuthLogoutRequest request) {
    try {

      return oAuthHandler.handleLogout(request);
    } catch (Exception exception) {

      return logoutErrorHandler.handle(exception);
    }
  }
}
