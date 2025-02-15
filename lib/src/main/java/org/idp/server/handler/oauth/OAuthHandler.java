package org.idp.server.handler.oauth;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.OAuthRequestDelegate;
import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.OAuthSessionKey;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.request.OAuthLogoutParameters;
import org.idp.server.oauth.view.OAuthViewData;
import org.idp.server.oauth.view.OAuthViewDataCreator;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthViewDataResponse handleViewData(
      OAuthViewDataRequest request, OAuthRequestDelegate oAuthRequestDelegate) {
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationRequestIdentifier);
    ClientId clientId = authorizationRequest.clientId();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);

    OAuthSession session = oAuthRequestDelegate.findSession(authorizationRequest.sessionKey());

    OAuthViewDataCreator creator =
        new OAuthViewDataCreator(
            authorizationRequest, serverConfiguration, clientConfiguration, session);
    OAuthViewData oAuthViewData = creator.create();

    return new OAuthViewDataResponse(OAuthViewDataStatus.OK, oAuthViewData);
  }

  public AuthorizationRequest handleGettingData(
      AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    return authorizationRequestRepository.get(authorizationRequestIdentifier);
  }

  public OAuthLogoutResponse handleLogout(OAuthLogoutRequest request,  OAuthRequestDelegate delegate) {
    OAuthLogoutParameters parameters = request.toParameters();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    OAuthSessionKey oAuthSessionKey = new OAuthSessionKey(tokenIssuer.value(), parameters.clientId().value());
    OAuthSession session = delegate.findSession(oAuthSessionKey);
    delegate.deleteSession(oAuthSessionKey);
    return new OAuthLogoutResponse(OAuthLogoutStatus.OK, "http://localhost:3000");
  }
}
