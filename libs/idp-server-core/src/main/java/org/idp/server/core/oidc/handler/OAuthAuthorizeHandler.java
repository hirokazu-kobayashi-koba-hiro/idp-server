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

package org.idp.server.core.oidc.handler;

import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.OAuthAuthorizeContext;
import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionDelegate;
import org.idp.server.core.oidc.OAuthSessionKey;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrantCreator;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.io.OAuthAuthorizeRequest;
import org.idp.server.core.oidc.repository.*;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.response.*;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.OAuthTokenFactory;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.core.oidc.validator.OAuthAuthorizeRequestValidator;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** OAuthAuthorizeHandler */
public class OAuthAuthorizeHandler {

  AuthorizationResponseCreators creators;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  OAuthTokenRepository oAuthTokenRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public OAuthAuthorizeHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.creators = new AuthorizationResponseCreators();
  }

  public AuthorizationResponse handle(
      OAuthAuthorizeRequest request, OAuthSessionDelegate delegate) {

    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    User user = request.user();
    Authentication authentication = request.authentication();
    CustomProperties customProperties = request.toCustomProperties();

    OAuthAuthorizeRequestValidator validator =
        new OAuthAuthorizeRequestValidator(
            authorizationRequestIdentifier, user, authentication, customProperties);
    validator.validate();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);

    OAuthAuthorizeContext context =
        new OAuthAuthorizeContext(
            authorizationRequest,
            user,
            authentication,
            customProperties,
            authorizationServerConfiguration,
            clientConfiguration);

    AuthorizationResponseCreator authorizationResponseCreator =
        creators.get(context.responseType());
    AuthorizationResponse authorizationResponse = authorizationResponseCreator.create(context);

    AuthorizationGrant authorizationGrant = context.authorize();
    if (authorizationResponse.hasAuthorizationCode()) {
      AuthorizationCodeGrant authorizationCodeGrant =
          AuthorizationCodeGrantCreator.create(context, authorizationResponse);
      authorizationCodeGrantRepository.register(tenant, authorizationCodeGrant);
    }

    if (authorizationResponse.hasAccessToken()) {
      OAuthToken oAuthToken = OAuthTokenFactory.create(authorizationResponse, authorizationGrant);
      oAuthTokenRepository.register(tenant, oAuthToken);
    }

    OAuthSessionKey oAuthSessionKey =
        new OAuthSessionKey(tenant.identifierValue(), requestedClientId.value());
    OAuthSession session =
        OAuthSession.create(oAuthSessionKey, user, authentication, authorizationRequest.maxAge());
    delegate.registerSession(session);

    return authorizationResponse;
  }
}
