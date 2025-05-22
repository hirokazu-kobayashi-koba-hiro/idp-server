/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba;

import org.idp.server.core.extension.ciba.clientnotification.NotificationClient;
import org.idp.server.core.extension.ciba.handler.CibaAuthorizeHandler;
import org.idp.server.core.extension.ciba.handler.CibaDenyHandler;
import org.idp.server.core.extension.ciba.handler.CibaRequestErrorHandler;
import org.idp.server.core.extension.ciba.handler.CibaRequestHandler;
import org.idp.server.core.extension.ciba.handler.io.*;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class DefaultCibaProtocol implements CibaProtocol {

  CibaRequestHandler cibaRequestHandler;
  CibaAuthorizeHandler cibaAuthorizeHandler;
  CibaDenyHandler cibaDenyHandler;
  CibaRequestErrorHandler errorHandler;
  UserQueryRepository userQueryRepository;
  CibaGrantRepository cibaGrantRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(DefaultCibaProtocol.class);

  public DefaultCibaProtocol(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      UserQueryRepository userQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.cibaRequestHandler =
        new CibaRequestHandler(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.cibaAuthorizeHandler =
        new CibaAuthorizeHandler(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            new NotificationClient(),
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.cibaDenyHandler =
        new CibaDenyHandler(
            cibaGrantRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.errorHandler = new CibaRequestErrorHandler();
    this.cibaGrantRepository = cibaGrantRepository;
    this.userQueryRepository = userQueryRepository;
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  /**
   * Handles a CIBA request by processing the request parameters, verifying the client and server
   * configurations, authenticating the client, and initiating the user authentication process.
   *
   * <p>This method performs the following steps:
   *
   * <ul>
   *   <li>Converts the incoming request into a set of parameters and identifies the token issuer.
   *   <li>Retrieves the server and client configurations based on the token issuer and client ID.
   *   <li>Analyzes the request parameters to determine the appropriate CIBA request pattern.
   *   <li>Creates a CIBA request context using the server and client configurations and request
   *       parameters.
   *   <li>Verifies the request using the CIBA request verifier and authenticates the client.
   *   <li>Initiates user authentication and notifies the user.
   *   <li>Generates a backchannel authentication response and registers the request and CIBA grant.
   * </ul>
   *
   * @param request the CIBA request containing client authentication data and parameters
   * @return a {@link CibaRequestResult} containing the status and the backchannel authentication
   *     response
   */
  public CibaRequestResult request(CibaRequest request) {
    try {

      CibaRequestContext cibaRequestContext = cibaRequestHandler.handleRequest(request);

      return new CibaRequestResult(CibaRequestStatus.OK, cibaRequestContext);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public CibaIssueResponse issueResponse(CibaIssueRequest issueRequest) {

    return cibaRequestHandler.handleIssueResponse(issueRequest);
  }

  public BackchannelAuthenticationRequest get(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier) {

    return cibaRequestHandler.handleGettingRequest(
        tenant, backchannelAuthenticationRequestIdentifier);
  }

  public CibaAuthorizeResponse authorize(CibaAuthorizeRequest request) {
    try {
      return cibaAuthorizeHandler.handle(request);
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      return new CibaAuthorizeResponse(CibaAuthorizeStatus.SERVER_ERROR);
    }
  }

  public CibaDenyResponse deny(CibaDenyRequest request) {
    try {
      return cibaDenyHandler.handle(request);
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      return new CibaDenyResponse(CibaDenyStatus.SERVER_ERROR);
    }
  }
}
