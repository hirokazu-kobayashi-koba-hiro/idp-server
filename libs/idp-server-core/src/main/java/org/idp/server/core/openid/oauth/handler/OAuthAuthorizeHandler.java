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

package org.idp.server.core.openid.oauth.handler;

import java.util.UUID;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrantCreator;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.OAuthAuthorizeContext;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.io.OAuthAuthorizeRequest;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.response.AuthorizationResponse;
import org.idp.server.core.openid.oauth.response.AuthorizationResponseCreator;
import org.idp.server.core.openid.oauth.response.AuthorizationResponseCreators;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.extension.DeniedScopes;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.validator.OAuthAuthorizeRequestValidator;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.OAuthTokenFactory;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** OAuthAuthorizeHandler */
public class OAuthAuthorizeHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthAuthorizeHandler.class);
  AuthorizationResponseCreators creators;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;

  public OAuthAuthorizeHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
    this.creators = new AuthorizationResponseCreators();
  }

  public AuthorizationResponse handle(OAuthAuthorizeRequest request) {

    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    User user = request.user();
    Authentication authentication = request.authentication();
    CustomProperties customProperties = request.toCustomProperties();
    DeniedScopes deniedScopes = request.toDeniedScopes();

    OAuthAuthorizeRequestValidator validator =
        new OAuthAuthorizeRequestValidator(
            authorizationRequestIdentifier, user, authentication, customProperties);
    validator.validate();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.requestedClientId();
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
            deniedScopes,
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
      oAuthTokenCommandRepository.register(tenant, oAuthToken);
    }

    // Register AuthorizationGranted at authorization time (enables SSO before token exchange)
    registerOrUpdateAuthorizationGranted(tenant, authorizationGrant);

    return authorizationResponse;
  }

  private void registerOrUpdateAuthorizationGranted(
      Tenant tenant, AuthorizationGrant authorizationGrant) {
    AuthorizationGranted latest =
        authorizationGrantedRepository.find(
            tenant, authorizationGrant.requestedClientId(), authorizationGrant.user());

    if (latest.exists()) {
      AuthorizationGranted merge = latest.merge(authorizationGrant);
      authorizationGrantedRepository.update(tenant, merge);
      return;
    }
    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(authorizationGrantedIdentifier, authorizationGrant);
    authorizationGrantedRepository.register(tenant, authorizationGranted);
  }
}
