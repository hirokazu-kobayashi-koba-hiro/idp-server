package org.idp.server.core.handler.federation;

import org.idp.server.core.federation.FederatableIdProviderConfiguration;
import org.idp.server.core.federation.FederatableIdProviderConfigurationRepository;
import org.idp.server.core.federation.FederationAuthorizationRequest;
import org.idp.server.core.federation.FederationAuthorizationRequestCreator;
import org.idp.server.core.handler.federation.io.*;

public class FederationHandler {

  FederatableIdProviderConfigurationRepository federatableIdProviderConfigurationRepository;

  public FederationHandler(
      FederatableIdProviderConfigurationRepository idProviderConfigurationRepository) {
    this.federatableIdProviderConfigurationRepository = idProviderConfigurationRepository;
  }

  public FederationRequestResponse handleRequest(FederationRequest federationRequest) {

    FederatableIdProviderConfiguration federatableIdProviderConfiguration =
        federatableIdProviderConfigurationRepository.get(
            federationRequest.getFederatableIdProviderId());

    FederationAuthorizationRequestCreator authorizationRequestCreator =
        new FederationAuthorizationRequestCreator(
            federatableIdProviderConfiguration, federationRequest.customParameters());
    FederationAuthorizationRequest federationAuthorizationRequest =
        authorizationRequestCreator.create();

    return new FederationRequestResponse(
        FederationRequestStatus.REDIRECABLE_OK,
        federationAuthorizationRequest,
        federatableIdProviderConfiguration);
  }

  public FederationCallbackResponse handleCallback(
      FederationCallbackRequest federationCallbackRequest) {

    FederationCallbackResponse federationCallbackResponse = new FederationCallbackResponse();
    return federationCallbackResponse;
  }
}
