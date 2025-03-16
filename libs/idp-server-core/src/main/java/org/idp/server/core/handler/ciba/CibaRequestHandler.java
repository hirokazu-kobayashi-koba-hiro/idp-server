package org.idp.server.core.handler.ciba;

import java.util.UUID;
import org.idp.server.core.ciba.*;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.grant.CibaGrantFactory;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.ciba.response.BackchannelAuthenticationResponseBuilder;
import org.idp.server.core.ciba.service.CibaContextServices;
import org.idp.server.core.ciba.service.CibaRequestContextService;
import org.idp.server.core.ciba.service.UserService;
import org.idp.server.core.ciba.verifier.CibaRequestVerifier;
import org.idp.server.core.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.ciba.io.CibaRequest;
import org.idp.server.core.handler.ciba.io.CibaRequestResponse;
import org.idp.server.core.handler.ciba.io.CibaRequestStatus;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.oauth.TokenIssuer;

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
  CibaContextServices contextServices;
  ClientAuthenticatorHandler clientAuthenticatorHandler;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  /**
   * Constructs a CibaRequestHandler with the specified repository and configuration dependencies.
   *
   * @param backchannelAuthenticationRequestRepository the repository to register backchannel
   *     authentication requests
   * @param cibaGrantRepository the repository to store CIBA grants
   * @param serverConfigurationRepository the repository to retrieve server configuration details
   * @param clientConfigurationRepository the repository to retrieve client configuration details
   */
  public CibaRequestHandler(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.cibaGrantRepository = cibaGrantRepository;
    this.contextServices = new CibaContextServices();
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
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
   * @param delegate a delegate interface for handling user-related operations
   * @return a {@link CibaRequestResponse} containing the status and the backchannel authentication
   *     response
   */
  public CibaRequestResponse handle(CibaRequest request, CibaRequestDelegate delegate) {
    CibaRequestParameters parameters = request.toParameters();
    TokenIssuer tokenIssuer = request.toTokenIssuer();

    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, request.clientId());
    CibaRequestAnalyzer analyzer = new CibaRequestAnalyzer(parameters);
    CibaRequestPattern pattern = analyzer.analyze();
    CibaRequestContextService cibaRequestContextService = contextServices.get(pattern);

    CibaRequestContext context =
        cibaRequestContextService.create(
            request.clientSecretBasic(), parameters, serverConfiguration, clientConfiguration);

    CibaRequestVerifier verifier = new CibaRequestVerifier(context);
    verifier.verify();
    clientAuthenticatorHandler.authenticate(context);

    UserService userService = new UserService(delegate, context);

    User user = userService.getAndNotify();

    BackchannelAuthenticationResponse response =
        new BackchannelAuthenticationResponseBuilder()
            .add(new AuthReqId(UUID.randomUUID().toString()))
            .add(context.expiresIn())
            .add(context.interval())
            .build();

    backchannelAuthenticationRequestRepository.register(context.backchannelAuthenticationRequest());
    // FIXME consider param
    CibaGrantFactory cibaGrantFactory =
        new CibaGrantFactory(context, response, user, new Authentication());
    CibaGrant cibaGrant = cibaGrantFactory.create();
    cibaGrantRepository.register(cibaGrant);

    return new CibaRequestResponse(CibaRequestStatus.OK, response);
  }
}
