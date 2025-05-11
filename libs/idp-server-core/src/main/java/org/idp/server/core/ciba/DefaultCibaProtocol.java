package org.idp.server.core.ciba;

import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
import org.idp.server.basic.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.ciba.clientnotification.NotificationClient;
import org.idp.server.core.ciba.handler.CibaAuthorizeHandler;
import org.idp.server.core.ciba.handler.CibaDenyHandler;
import org.idp.server.core.ciba.handler.CibaRequestErrorHandler;
import org.idp.server.core.ciba.handler.CibaRequestHandler;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

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
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.cibaRequestHandler =
        new CibaRequestHandler(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository);
    this.cibaAuthorizeHandler =
        new CibaAuthorizeHandler(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            new NotificationClient(),
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository);
    this.cibaDenyHandler =
        new CibaDenyHandler(
            cibaGrantRepository,
            authorizationServerConfigurationRepository,
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
