package org.idp.server.core.ciba;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.basic.dependency.protcol.DefaultAuthorizationProvider;
import org.idp.server.core.ciba.clientnotification.NotificationClient;
import org.idp.server.core.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.grant.CibaGrantFactory;
import org.idp.server.core.ciba.handler.CibaAuthorizeHandler;
import org.idp.server.core.ciba.handler.CibaDenyHandler;
import org.idp.server.core.ciba.handler.CibaRequestErrorHandler;
import org.idp.server.core.ciba.handler.CibaRequestHandler;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.extension.Pairs;

public class DefaultCibaProtocol implements CibaProtocol {

  CibaRequestHandler cibaRequestHandler;
  CibaAuthorizeHandler cibaAuthorizeHandler;
  CibaDenyHandler cibaDenyHandler;
  CibaRequestErrorHandler errorHandler;
  UserRepository userRepository;
  CibaGrantRepository cibaGrantRepository;
  Logger log = Logger.getLogger(DefaultCibaProtocol.class.getName());

  public DefaultCibaProtocol(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      UserRepository userRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.cibaRequestHandler =
        new CibaRequestHandler(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            serverConfigurationRepository,
            clientConfigurationRepository);
    this.cibaAuthorizeHandler =
        new CibaAuthorizeHandler(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            new NotificationClient(),
            serverConfigurationRepository,
            clientConfigurationRepository);
    this.cibaDenyHandler =
        new CibaDenyHandler(
            cibaGrantRepository, serverConfigurationRepository, clientConfigurationRepository);
    ;
    this.errorHandler = new CibaRequestErrorHandler();
    this.cibaGrantRepository = cibaGrantRepository;
    this.userRepository = userRepository;
  }

  @Override
  public AuthorizationProtocolProvider authorizationProtocolProvider() {
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
   * @return a {@link CibaRequestResponse} containing the status and the backchannel authentication
   *     response
   */
  public CibaRequestResponse request(CibaRequest request) {
    try {
      Tenant tenant = request.tenant();
      Pairs<CibaRequestContext, BackchannelAuthenticationResponse> result =
          cibaRequestHandler.handle(request);
      CibaRequestContext context = result.getLeft();
      BackchannelAuthenticationResponse response = result.getRight();

      BackchannelAuthenticationRequest backchannelAuthenticationRequest =
          context.backchannelAuthenticationRequest();

      // TODO consider logic
      User user =
          userRepository.findBy(
              tenant, backchannelAuthenticationRequest.loginHint().value(), "idp-server");

      if (!user.exists()) {
        throw new BackchannelAuthenticationBadRequestException(
            "unknown_user_id",
            "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint).");
      }
      //      if (context.hasUserCode()) {
      //        boolean authenticationResult =
      //                cibaRequestDelegate.authenticate(context.tenantIdentifier(), user,
      // context.userCode());
      //        if (!authenticationResult) {
      //          throw new BackchannelAuthenticationBadRequestException(
      //                  "invalid_user_code", "backchannel authentication request user_code is
      // invalid");
      //        }
      //      }

      CibaGrantFactory cibaGrantFactory =
          new CibaGrantFactory(context, response, user, new Authentication());
      CibaGrant cibaGrant = cibaGrantFactory.create();
      cibaGrantRepository.register(tenant, cibaGrant);

      return new CibaRequestResponse(CibaRequestStatus.OK, context, response, user);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public CibaAuthorizeResponse authorize(CibaAuthorizeRequest request) {
    try {
      return cibaAuthorizeHandler.handle(request);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new CibaAuthorizeResponse(CibaAuthorizeStatus.SERVER_ERROR);
    }
  }

  public CibaDenyResponse deny(CibaDenyRequest request) {
    try {
      return cibaDenyHandler.handle(request);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new CibaDenyResponse(CibaDenyStatus.SERVER_ERROR);
    }
  }
}
