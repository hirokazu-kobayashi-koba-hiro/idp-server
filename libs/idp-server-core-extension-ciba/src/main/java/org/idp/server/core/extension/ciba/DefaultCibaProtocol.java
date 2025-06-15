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

package org.idp.server.core.extension.ciba;

import org.idp.server.core.extension.ciba.clientnotification.NotificationClient;
import org.idp.server.core.extension.ciba.handler.*;
import org.idp.server.core.extension.ciba.handler.io.*;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.extension.ciba.user.UserHintResolver;
import org.idp.server.core.extension.ciba.user.UserHintResolvers;
import org.idp.server.core.extension.ciba.verifier.additional.CibaRequestAdditionalVerifiers;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class DefaultCibaProtocol implements CibaProtocol {

  CibaRequestHandler cibaRequestHandler;
  CibaAuthorizeHandler cibaAuthorizeHandler;
  CibaDenyHandler denyHandler;
  CibaRequestErrorHandler errorHandler;
  CibaAuthorizeRequestErrorHandler authorizeErrorHandler;
  CibaDenyRequestErrorHandler denyErrorHandler;
  UserQueryRepository userQueryRepository;
  CibaGrantRepository cibaGrantRepository;
  LoggerWrapper logger = LoggerWrapper.getLogger(DefaultCibaProtocol.class);

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
    this.denyHandler =
        new CibaDenyHandler(
            cibaGrantRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.errorHandler = new CibaRequestErrorHandler();
    this.authorizeErrorHandler = new CibaAuthorizeRequestErrorHandler();
    this.denyErrorHandler = new CibaDenyRequestErrorHandler();
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
   * @return containing the status and the backchannel authentication response
   */
  public CibaIssueResponse request(
      CibaRequest request,
      UserHintResolvers userHintResolvers,
      CibaRequestAdditionalVerifiers additionalVerifiers) {

    long startAt = System.currentTimeMillis(); // ← 開始時間記録

    try {
      CibaRequestContext cibaRequestContext = cibaRequestHandler.handleRequest(request);

      UserHintResolver userHintResolver = userHintResolvers.get(cibaRequestContext.userHintType());

      long resolveStart = System.currentTimeMillis(); // ← resolve 開始時間
      User user =
          userHintResolver.resolve(
              cibaRequestContext.tenant(),
              cibaRequestContext.userHint(),
              cibaRequestContext.userHintRelatedParams(),
              userQueryRepository);
      long resolveTime = System.currentTimeMillis() - resolveStart;

      long verifyStart = System.currentTimeMillis(); // ← verify 開始時間
      additionalVerifiers.verify(cibaRequestContext, user);
      long verifyTime = System.currentTimeMillis() - verifyStart;

      long issueStart = System.currentTimeMillis(); // ← issue 開始時間
      CibaIssueResponse response =
          cibaRequestHandler.handleIssueResponse(
              new CibaIssueRequest(cibaRequestContext.tenant(), cibaRequestContext, user));
      long issueTime = System.currentTimeMillis() - issueStart;

      long totalTime = System.currentTimeMillis() - startAt;

      logger.info(
          "[CIBA] request() timing: total={}ms (resolveUser={}ms, verify={}ms, issueResponse={}ms)",
          totalTime,
          resolveTime,
          verifyTime,
          issueTime);

      return response;

    } catch (Exception exception) {
      long totalTime = System.currentTimeMillis() - startAt;
      logger.warn("[CIBA] request() failed after {}ms: {}", totalTime, exception.getMessage());
      return errorHandler.handle(exception);
    }
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

      return authorizeErrorHandler.handle(exception);
    }
  }

  public CibaDenyResponse deny(CibaDenyRequest request) {
    try {
      return denyHandler.handle(request);
    } catch (Exception exception) {

      return denyErrorHandler.handle(exception);
    }
  }
}
