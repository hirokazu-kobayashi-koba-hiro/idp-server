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

import org.idp.server.core.oidc.*;
import org.idp.server.core.oidc.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.context.*;
import org.idp.server.core.oidc.factory.RequestObjectFactories;
import org.idp.server.core.oidc.gateway.RequestObjectGateway;
import org.idp.server.core.oidc.grant_management.AuthorizationGranted;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.io.OAuthPushedRequest;
import org.idp.server.core.oidc.io.OAuthRequest;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.core.oidc.validator.OAuthRequestValidator;
import org.idp.server.core.oidc.verifier.OAuthRequestVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** OAuthRequestHandler */
public class OAuthRequestHandler {

  OAuthRequestContextCreators oAuthRequestContextCreators;
  OAuthRequestVerifier verifier;
  ClientAuthenticationHandler clientAuthenticationHandler;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuthorizationGrantedRepository grantedRepository;

  public OAuthRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      RequestObjectGateway requestObjectGateway,
      RequestObjectFactories requestObjectFactories,
      AuthorizationGrantedRepository grantedRepository) {
    this.oAuthRequestContextCreators =
        new OAuthRequestContextCreators(
            requestObjectGateway, authorizationRequestRepository, requestObjectFactories);
    this.verifier = new OAuthRequestVerifier();
    this.clientAuthenticationHandler = new ClientAuthenticationHandler();
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.grantedRepository = grantedRepository;
  }

  public OAuthPushedRequestContext handlePushedRequest(OAuthPushedRequest pushedRequest) {
    OAuthRequestParameters requestParameters = pushedRequest.toOAuthRequestParameters();
    Tenant tenant = pushedRequest.tenant();
    OAuthRequestValidator validator = new OAuthRequestValidator(tenant, requestParameters);
    validator.validate();

    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestParameters.clientId());

    OAuthRequestPattern oAuthRequestPattern = requestParameters.analyzePattern();
    OAuthRequestContextCreator oAuthRequestContextCreator =
        oAuthRequestContextCreators.get(oAuthRequestPattern);

    OAuthRequestContext context =
        oAuthRequestContextCreator.create(
            tenant, requestParameters, authorizationServerConfiguration, clientConfiguration);
    verifier.verify(context);

    OAuthPushedRequestContext oAuthPushedRequestContext =
        new OAuthPushedRequestContext(
            context,
            pushedRequest.clientSecretBasic(),
            pushedRequest.toClientCert(),
            pushedRequest.toBackchannelParameters());
    clientAuthenticationHandler.authenticate(oAuthPushedRequestContext);

    authorizationRequestRepository.register(tenant, context.authorizationRequest());

    return oAuthPushedRequestContext;
  }

  public OAuthRequestContext handleRequest(
      OAuthRequest oAuthRequest, OAuthSessionDelegate delegate) {
    OAuthRequestParameters parameters = oAuthRequest.toParameters();
    Tenant tenant = oAuthRequest.tenant();
    OAuthRequestValidator validator = new OAuthRequestValidator(tenant, parameters);
    validator.validate();

    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, parameters.clientId());

    OAuthRequestPattern oAuthRequestPattern = parameters.analyzePattern();
    OAuthRequestContextCreator oAuthRequestContextCreator =
        oAuthRequestContextCreators.get(oAuthRequestPattern);

    OAuthRequestContext context =
        oAuthRequestContextCreator.create(
            tenant, parameters, authorizationServerConfiguration, clientConfiguration);
    verifier.verify(context);

    if (!context.isPushedRequest()) {
      authorizationRequestRepository.register(tenant, context.authorizationRequest());
    }

    OAuthSession session = delegate.find(context.sessionKey());

    if (session.exists()) {
      context.setSession(session);

      AuthorizationGranted authorizationGranted =
          grantedRepository.find(tenant, parameters.clientId(), session.user());
      context.setAuthorizationGranted(authorizationGranted);
    }

    return context;
  }
}
