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

public class CibaRequestHandler {

  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  CibaGrantRepository cibaGrantRepository;
  CibaContextServices contextServices;
  ClientAuthenticatorHandler clientAuthenticatorHandler;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

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
