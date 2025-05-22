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


package org.idp.server.core.extension.ciba.handler;

import java.util.UUID;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.extension.ciba.CibaRequestPattern;
import org.idp.server.core.extension.ciba.context.CibaContextCreators;
import org.idp.server.core.extension.ciba.context.CibaRequestContextCreator;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.grant.CibaGrantFactory;
import org.idp.server.core.extension.ciba.handler.io.CibaIssueRequest;
import org.idp.server.core.extension.ciba.handler.io.CibaIssueResponse;
import org.idp.server.core.extension.ciba.handler.io.CibaRequest;
import org.idp.server.core.extension.ciba.handler.io.CibaRequestStatus;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationResponseBuilder;
import org.idp.server.core.extension.ciba.verifier.CibaRequestVerifier;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Handles CIBA (Client Initiated Backchannel Authentication) requests.
 *
 * <p>The CibaRequestHandler class processes backchannel authentication requests as per the CIBA
 * protocol. It verifies client and server configurations, authenticates clients, and initiates the
 * user authentication process via the CIBA flow. This class is responsible for generating and
 * registering backchannel authentication requests and CIBA grants.
 *
 * <p>Typical usage includes receiving a CibaRequest, processing it to determine the authentication
 * parameters and context, verifying the request, and generating a backchannel authentication
 * response.
 */
public class CibaRequestHandler {

  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  CibaGrantRepository cibaGrantRepository;
  CibaContextCreators contextCreators;
  ClientAuthenticationHandler clientAuthenticationHandler;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  /**
   * Constructs a CibaRequestHandler with the specified repository and configuration dependencies.
   *
   * @param backchannelAuthenticationRequestRepository the repository to register backchannel
   *     authentication requests
   * @param cibaGrantRepository the repository to store CIBA grants
   * @param authorizationServerConfigurationQueryRepository the repository to retrieve server
   *     configuration details
   * @param clientConfigurationQueryRepository the repository to retrieve client configuration
   *     details
   */
  public CibaRequestHandler(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.cibaGrantRepository = cibaGrantRepository;
    this.contextCreators = new CibaContextCreators();
    this.clientAuthenticationHandler = new ClientAuthenticationHandler();
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public CibaRequestContext handleRequest(CibaRequest request) {
    CibaRequestParameters parameters = request.toParameters();
    Tenant tenant = request.tenant();

    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, request.clientId());
    CibaRequestPattern pattern = parameters.analyze();
    CibaRequestContextCreator cibaRequestContextCreator = contextCreators.get(pattern);

    CibaRequestContext context =
        cibaRequestContextCreator.create(
            tenant,
            request.clientSecretBasic(),
            request.toClientCert(),
            parameters,
            authorizationServerConfiguration,
            clientConfiguration);

    CibaRequestVerifier verifier = new CibaRequestVerifier(context);
    verifier.verify();
    clientAuthenticationHandler.authenticate(context);

    return context;
  }

  public CibaIssueResponse handleIssueResponse(CibaIssueRequest request) {
    CibaRequestContext context = request.context();
    Tenant tenant = request.tenant();

    BackchannelAuthenticationResponse response =
        new BackchannelAuthenticationResponseBuilder()
            .add(new AuthReqId(UUID.randomUUID().toString()))
            .add(context.expiresIn())
            .add(context.interval())
            .build();

    backchannelAuthenticationRequestRepository.register(
        tenant, context.backchannelAuthenticationRequest());

    User user = request.user();
    CibaGrantFactory cibaGrantFactory =
        new CibaGrantFactory(context, response, user, new Authentication());
    CibaGrant cibaGrant = cibaGrantFactory.create();
    cibaGrantRepository.register(tenant, cibaGrant);

    return new CibaIssueResponse(CibaRequestStatus.OK, context, response, user);
  }

  public BackchannelAuthenticationRequest handleGettingRequest(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier) {
    return backchannelAuthenticationRequestRepository.find(
        tenant, backchannelAuthenticationRequestIdentifier);
  }
}
