package org.idp.server.handler.oauth;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.view.OAuthViewData;
import org.idp.server.oauth.view.OAuthViewDataCreator;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthViewDataHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthViewDataHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthViewDataResponse handle(OAuthViewDataRequest request) {
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationRequestIdentifier);
    ClientId clientId = authorizationRequest.clientId();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);
    OAuthViewDataCreator creator =
        new OAuthViewDataCreator(authorizationRequest, serverConfiguration, clientConfiguration);
    OAuthViewData oAuthViewData = creator.create();

    return new OAuthViewDataResponse(OAuthViewDataStatus.OK, oAuthViewData);
  }
}
