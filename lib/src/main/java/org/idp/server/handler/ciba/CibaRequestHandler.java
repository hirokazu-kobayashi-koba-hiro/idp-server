package org.idp.server.handler.ciba;

import java.util.UUID;
import org.idp.server.ciba.*;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.grant.CibaGrantFactory;
import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.repository.CibaGrantRepository;
import org.idp.server.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.ciba.response.BackchannelAuthenticationResponseBuilder;
import org.idp.server.ciba.service.CibaContextServices;
import org.idp.server.ciba.service.CibaRequestContextService;
import org.idp.server.ciba.service.UserService;
import org.idp.server.ciba.verifier.CibaRequestVerifier;
import org.idp.server.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.ciba.io.CibaRequest;
import org.idp.server.handler.ciba.io.CibaRequestResponse;
import org.idp.server.handler.ciba.io.CibaRequestStatus;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.oauth.TokenIssuer;

/**
 * Handles CIBA (Client Initiated Backchannel Authentication) requests.
 *
 * <p>The CibaRequestHandler class processes backchannel authentication requests
 * as per the CIBA protocol. It verifies client and server configurations,
 * authenticates clients, and initiates the user authentication process via the
 * CIBA flow. This class is responsible for generating and registering
 * backchannel authentication requests and CIBA grants.
 *
 * <p>Typical usage includes receiving a CibaRequest, processing it to determine
 * the authentication parameters and context, verifying the request, and
 * generating a backchannel authentication response.
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
   * @param backchannelAuthenticationRequestRepository the repository to register backchannel authentication requests
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
   * Handles a CIBA request by processing the request parameters, verifying the client and server configurations,
   * authenticating the client, and initiating the user authentication process.
   *
   * <p>This method performs the following steps:
   * <ul>
   *   <li>Converts the incoming request into a set of parameters and identifies the token issuer.</li>
   *   <li>Retrieves the server and client configurations based on the token issuer and client ID.</li>
   *   <li>Analyzes the request parameters to determine the appropriate CIBA request pattern.</li>
   *   <li>Creates a CIBA request context using the server and client configurations and request parameters.</li>
   *   <li>Verifies the request using the CIBA request verifier and authenticates the client.</li>
   *   <li>Initiates user authentication and notifies the user.</li>
   *   <li>Generates a backchannel authentication response and registers the request and CIBA grant.</li>
   * </ul>
   *
   * @param request the CIBA request containing client authentication data and parameters
   * @param delegate a delegate interface for handling user-related operations
   * @return a {@link CibaRequestResponse} containing the status and the backchannel authentication response
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
