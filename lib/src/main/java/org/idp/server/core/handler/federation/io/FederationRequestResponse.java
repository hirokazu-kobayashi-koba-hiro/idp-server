package org.idp.server.core.handler.federation.io;

import org.idp.server.core.federation.FederatableIdProviderConfiguration;
import org.idp.server.core.federation.FederationAuthorizationRequest;

public class FederationRequestResponse {

  FederationRequestStatus status;
  FederationAuthorizationRequest federationAuthorizationRequest;
  FederatableIdProviderConfiguration federatableIdProviderConfiguration;

  public FederationRequestResponse(FederationRequestStatus status) {
    this.status = status;
  }

  public FederationRequestResponse(
      FederationRequestStatus status,
      FederationAuthorizationRequest federationAuthorizationRequest,
      FederatableIdProviderConfiguration federatableIdProviderConfiguration) {
    this.status = status;
    this.federationAuthorizationRequest = federationAuthorizationRequest;
    this.federatableIdProviderConfiguration = federatableIdProviderConfiguration;
  }

  public FederationRequestStatus status() {
    return status;
  }

  public FederationAuthorizationRequest federationAuthorizationRequest() {
    return federationAuthorizationRequest;
  }

  public FederatableIdProviderConfiguration federatableIdProviderConfiguration() {
    return federatableIdProviderConfiguration;
  }

  public String authorizationRequestUrl() {
    return federationAuthorizationRequest.url();
  }
}
