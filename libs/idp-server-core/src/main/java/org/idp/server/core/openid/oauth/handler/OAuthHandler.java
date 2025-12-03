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

import org.idp.server.core.openid.oauth.OAuthSession;
import org.idp.server.core.openid.oauth.OAuthSessionDelegate;
import org.idp.server.core.openid.oauth.OAuthSessionKey;
import org.idp.server.core.openid.oauth.OAuthUserDelegate;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.io.*;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.view.OAuthViewData;
import org.idp.server.core.openid.oauth.view.OAuthViewDataCreator;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public OAuthHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public OAuthViewDataResponse handleViewData(
      OAuthViewDataRequest request,
      OAuthSessionDelegate oAuthSessionDelegate,
      OAuthUserDelegate oAuthUserDelegate) {
    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.requestedClientId();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);

    OAuthSession session = oAuthSessionDelegate.find(authorizationRequest.sessionKey());

    if (session != null && session.hasUser()) {
      boolean userExists = oAuthUserDelegate.userExists(tenant, session.user().userIdentifier());
      if (!userExists) {
        session = null;
      }
    }

    OAuthViewDataCreator creator =
        new OAuthViewDataCreator(
            authorizationRequest,
            authorizationServerConfiguration,
            clientConfiguration,
            session,
            request.additionalViewData());
    OAuthViewData oAuthViewData = creator.create();

    return new OAuthViewDataResponse(OAuthViewDataStatus.OK, oAuthViewData);
  }

  public AuthorizationRequest handleGettingData(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    return authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
  }

  // TODO this is debug code.
  public OAuthLogoutResponse handleLogout(
      OAuthLogoutRequest request, OAuthSessionDelegate delegate) {

    OAuthLogoutParameters parameters = request.toParameters();
    Tenant tenant = request.tenant();

    OAuthSessionKey oAuthSessionKey =
        new OAuthSessionKey(tenant.identifierValue(), parameters.clientId().value());
    OAuthSession session = delegate.find(oAuthSessionKey);
    delegate.deleteSession(oAuthSessionKey);

    String redirectUri =
        parameters.hasPostLogoutRedirectUri() ? parameters.postLogoutRedirectUri().value() : "";

    if (parameters.hasPostLogoutRedirectUri()) {
      return new OAuthLogoutResponse(OAuthLogoutStatus.REDIRECABLE_FOUND, redirectUri);
    }

    return new OAuthLogoutResponse(OAuthLogoutStatus.OK, "");
  }
}
