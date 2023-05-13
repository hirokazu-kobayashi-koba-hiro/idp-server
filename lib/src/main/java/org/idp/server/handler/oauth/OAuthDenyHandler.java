package org.idp.server.handler.oauth;

import static org.idp.server.type.oauth.ResponseType.*;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.oauth.io.OAuthDenyRequest;
import org.idp.server.handler.oauth.io.OAuthDenyResponse;
import org.idp.server.handler.oauth.io.OAuthDenyStatus;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.response.*;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oauth.Error;

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
    RedirectUri redirectUri =
        authorizationRequest.hasRedirectUri()
            ? authorizationRequest.redirectUri()
            : clientConfiguration.getFirstRedirectUri();
    ResponseModeValue responseModeValue = new ResponseModeValue("?");

    AuthorizationErrorResponse errorResponse =
        new AuthorizationErrorResponseBuilder(redirectUri, responseModeValue, tokenIssuer)
                .add(authorizationRequest.state())
                .add(new Error(request.denyReason().name()))
            .add(new ErrorDescription(request.denyReason().errorDescription()))
            .build();
    return new OAuthDenyResponse(OAuthDenyStatus.OK, errorResponse);
  }
}
