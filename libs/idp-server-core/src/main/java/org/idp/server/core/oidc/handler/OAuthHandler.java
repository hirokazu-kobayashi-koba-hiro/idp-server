package org.idp.server.core.oidc.handler;

import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionDelegate;
import org.idp.server.core.oidc.OAuthSessionKey;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.io.*;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.request.OAuthLogoutParameters;
import org.idp.server.core.oidc.view.OAuthViewData;
import org.idp.server.core.oidc.view.OAuthViewDataCreator;

public class OAuthHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public OAuthHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public OAuthViewDataResponse handleViewData(
      OAuthViewDataRequest request, OAuthSessionDelegate oAuthSessionDelegate) {
    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);

    OAuthSession session = oAuthSessionDelegate.find(authorizationRequest.sessionKey());

    OAuthViewDataCreator creator =
        new OAuthViewDataCreator(
            authorizationRequest, authorizationServerConfiguration, clientConfiguration, session);
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
