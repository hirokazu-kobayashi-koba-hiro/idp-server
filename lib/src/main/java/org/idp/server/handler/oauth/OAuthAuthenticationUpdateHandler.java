package org.idp.server.handler.oauth;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.oauth.io.OAuthAuthenticationUpdateRequest;
import org.idp.server.handler.oauth.io.OAuthAuthenticationUpdateResponse;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthAuthenticationUpdateHandler */
public class OAuthAuthenticationUpdateHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthAuthenticationUpdateHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthAuthenticationUpdateResponse handle(OAuthAuthenticationUpdateRequest request) {
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    Authentication authentication = request.authentication();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationRequestIdentifier);
    ClientId clientId = authorizationRequest.clientId();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);

    return new OAuthAuthenticationUpdateResponse();
  }
}
