package org.idp.server.core.oauth.handler;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.OAuthSessionDelegate;
import org.idp.server.core.oauth.OAuthSessionKey;
import org.idp.server.core.oauth.io.*;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.request.OAuthLogoutParameters;
import org.idp.server.core.oauth.view.OAuthViewData;
import org.idp.server.core.oauth.view.OAuthViewDataCreator;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.RequestedClientId;

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
      OAuthViewDataRequest request, OAuthSessionDelegate oAuthSessionDelegate) {
    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.clientId();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, requestedClientId);

    OAuthSession session = oAuthSessionDelegate.find(authorizationRequest.sessionKey());

    OAuthViewDataCreator creator =
        new OAuthViewDataCreator(
            authorizationRequest, serverConfiguration, clientConfiguration, session);
    OAuthViewData oAuthViewData = creator.create();

    return new OAuthViewDataResponse(OAuthViewDataStatus.OK, oAuthViewData);
  }

  public AuthorizationRequest handleGettingData(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    return authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
  }

  public OAuthLogoutResponse handleLogout(
      OAuthLogoutRequest request, OAuthSessionDelegate delegate) {

    OAuthLogoutParameters parameters = request.toParameters();
    Tenant tenant = request.tenant();

    OAuthSessionKey oAuthSessionKey =
        new OAuthSessionKey(tenant.identifierValue(), parameters.clientId().value());
    OAuthSession session = delegate.find(oAuthSessionKey);
    delegate.deleteSession(oAuthSessionKey);

    String redirectUri =
        parameters.hasPostLogoutRedirectUri()
            ? parameters.postLogoutRedirectUri().value()
            : tenant.tokenIssuer().value();

    if (parameters.hasPostLogoutRedirectUri()) {
      return new OAuthLogoutResponse(OAuthLogoutStatus.REDIRECABLE_FOUND, redirectUri);
    }

    return new OAuthLogoutResponse(OAuthLogoutStatus.OK, "");
  }
}
