package org.idp.server.core.handler.federation;

import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.basic.jose.JoseHandler;
import org.idp.server.core.basic.jose.JoseInvalidException;
import org.idp.server.core.federation.*;
import org.idp.server.core.handler.federation.io.*;
import org.idp.server.core.oauth.identity.User;

import java.util.HashMap;
import java.util.Map;

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

    JoseContext joseContext = verifyAndParseIdToken(configuration, tokenResponse);

    FederationUserinfoResponse userinfoResponse =
        requestUserinfo(configuration, tokenResponse, joseContext);

    User existingUser =
        federationDelegate.find(
            session.tokenIssuer(), configuration.issuerName(), userinfoResponse.sub());

    FederationUserinfoResponseConvertor convertor =
        new FederationUserinfoResponseConvertor(existingUser, userinfoResponse, configuration);
    User user = convertor.convert();

    //    federationSessionRepository.delete(parameters.tokenIssuer(), session);

    return new FederationCallbackResponse(FederationCallbackStatus.OK, session, user);
  }

  private JoseContext verifyAndParseIdToken(
      FederatableIdProviderConfiguration configuration, FederationTokenResponse tokenResponse) {
    try {
      FederationJwksResponse jwksResponse =
          federationGateway.getJwks(new FederationJwksRequest(configuration.jwksUri()));

      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(tokenResponse.idToken(), jwksResponse.value(), "", "");

      joseContext.verifySignature();

      return joseContext;
    } catch (JoseInvalidException e) {

      throw new FederationInvalidIdTokenException("failed to parse id_token", e);
    }
  }

  private FederationUserinfoResponse requestUserinfo(
      FederatableIdProviderConfiguration configuration,
      FederationTokenResponse tokenResponse,
      JoseContext joseContext) {

    if (configuration.isFacebook()) {
      FederationUserinfoRequest userinfoRequest =
          new FederationUserinfoRequest(
              configuration.userinfoEndpoint(), tokenResponse.accessToken());

      return federationGateway.requestFacebookSpecificUerInfo(userinfoRequest);
    }

    //TODO examine at yahoo
    if (configuration.isYahoo()) {

      Map<String, Object> values = Map.of("sub", joseContext.claims().getSub());
      return new FederationUserinfoResponse(values);
    }

    FederationUserinfoRequest userinfoRequest =
        new FederationUserinfoRequest(
            configuration.userinfoEndpoint(), tokenResponse.accessToken());
    return federationGateway.requestUserInfo(userinfoRequest);
  }
}
