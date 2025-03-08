package org.idp.server.core.handler.federation;

import org.idp.server.core.federation.*;
import org.idp.server.core.handler.federation.io.*;
import org.idp.server.core.oauth.identity.User;

public class FederationHandler {

  FederatableIdProviderConfigurationRepository federatableIdProviderConfigurationRepository;
  FederationSessionRepository federationSessionRepository;
  FederationGateway federationGateway;

  public FederationHandler(
      FederatableIdProviderConfigurationRepository idProviderConfigurationRepository,
      FederationSessionRepository federationSessionRepository,
      FederationGateway federationGateway) {
    this.federatableIdProviderConfigurationRepository = idProviderConfigurationRepository;
    this.federationSessionRepository = federationSessionRepository;
    this.federationGateway = federationGateway;
  }

  public FederationRequestResponse handleRequest(FederationRequest federationRequest) {

    FederatableIdProviderConfiguration federatableIdProviderConfiguration =
        federatableIdProviderConfigurationRepository.get(
            federationRequest.federatableIdProviderId());

    FederationSessionCreator authorizationRequestCreator =
        new FederationSessionCreator(federatableIdProviderConfiguration, federationRequest);
    FederationSession federationSession = authorizationRequestCreator.create();

    federationSessionRepository.register(federationRequest.tokenIssuer(), federationSession);

    return new FederationRequestResponse(
        FederationRequestStatus.REDIRECABLE_OK,
        federationSession,
        federatableIdProviderConfiguration);
  }

  public FederationCallbackResponse handleCallback(
      FederationCallbackRequest federationCallbackRequest, FederationDelegate federationDelegate) {

    FederationCallbackParameters parameters = federationCallbackRequest.parameters();
    FederationSession session = federationSessionRepository.find(parameters.state());

    if (!session.exists()) {
      throw new FederationSessionNotFoundException("not found session");
    }

    FederatableIdProviderConfiguration configuration =
        federatableIdProviderConfigurationRepository.get(session.idpId());

    FederationTokenRequestCreator tokenRequestCreator =
        new FederationTokenRequestCreator(parameters, session, configuration);
    FederationTokenRequest tokenRequest = tokenRequestCreator.create();
    FederationTokenResponse tokenResponse = federationGateway.requestToken(tokenRequest);

    FederationUserinfoRequest userinfoRequest =
        new FederationUserinfoRequest(
            configuration.userinfoEndpoint(), tokenResponse.accessToken());
    FederationUserinfoResponse userinfoResponse =
        federationGateway.requestUserInfo(userinfoRequest);

    User existingUser =
        federationDelegate.find(
            session.tokenIssuer(), configuration.issuerName(), userinfoResponse.sub());

    FederationUserinfoResponseConvertor convertor =
        new FederationUserinfoResponseConvertor(existingUser, userinfoResponse, configuration);
    User user = convertor.convert();

    //    federationSessionRepository.delete(parameters.tokenIssuer(), session);

    return new FederationCallbackResponse(FederationCallbackStatus.OK, session, user);
  }
}
