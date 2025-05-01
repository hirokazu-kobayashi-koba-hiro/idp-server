package org.idp.server.core.ciba.handler;

import java.util.UUID;
import org.idp.server.core.ciba.*;
import org.idp.server.core.ciba.context.CibaContextCreators;
import org.idp.server.core.ciba.context.CibaRequestContextCreator;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.grant.CibaGrantFactory;
import org.idp.server.core.ciba.handler.io.CibaIssueRequest;
import org.idp.server.core.ciba.handler.io.CibaIssueResponse;
import org.idp.server.core.ciba.handler.io.CibaRequest;
import org.idp.server.core.ciba.handler.io.CibaRequestStatus;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.ciba.response.BackchannelAuthenticationResponseBuilder;
import org.idp.server.core.ciba.verifier.CibaRequestVerifier;
import org.idp.server.core.oidc.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ClientConfigurationRepository;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.basic.type.ciba.AuthReqId;

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
    this.contextCreators = new CibaContextCreators();
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public CibaRequestContext handleRequest(CibaRequest request) {
    CibaRequestParameters parameters = request.toParameters();
    Tenant tenant = request.tenant();

    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, request.clientId());
    CibaRequestPattern pattern = parameters.analyze();
    CibaRequestContextCreator cibaRequestContextCreator = contextCreators.get(pattern);

    CibaRequestContext context =
        cibaRequestContextCreator.create(
            request.clientSecretBasic(),
            request.toClientCert(),
            parameters,
            serverConfiguration,
            clientConfiguration);

    CibaRequestVerifier verifier = new CibaRequestVerifier(context);
    verifier.verify();
    clientAuthenticatorHandler.authenticate(context);

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
