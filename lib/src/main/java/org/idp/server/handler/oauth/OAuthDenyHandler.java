package org.idp.server.handler.oauth;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.oauth.io.OAuthDenyRequest;
import org.idp.server.handler.oauth.io.OAuthDenyResponse;
import org.idp.server.handler.oauth.io.OAuthDenyStatus;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.response.*;
import org.idp.server.type.oauth.*;

public class OAuthDenyHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthDenyHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthDenyResponse handle(OAuthDenyRequest request) {
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationRequestIdentifier);
    ClientId clientId = authorizationRequest.clientId();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);
    AuthorizationDenyErrorResponseCreator authorizationDenyErrorResponseCreator =
        new AuthorizationDenyErrorResponseCreator(
            authorizationRequest, request.denyReason(), serverConfiguration, clientConfiguration);

    AuthorizationErrorResponse errorResponse = authorizationDenyErrorResponseCreator.create();

    return new OAuthDenyResponse(OAuthDenyStatus.OK, errorResponse);
  }
}
