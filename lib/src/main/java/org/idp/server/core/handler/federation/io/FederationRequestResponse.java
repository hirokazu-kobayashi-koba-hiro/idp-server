package org.idp.server.core.handler.federation.io;

import org.idp.server.core.federation.FederatableIdProviderConfiguration;
import org.idp.server.core.federation.FederationSession;

public class FederationRequestResponse {

  FederationRequestStatus status;
  FederationSession federationSession;
  FederatableIdProviderConfiguration federatableIdProviderConfiguration;

  public FederationRequestResponse(FederationRequestStatus status) {
    this.status = status;
  }

  public FederationRequestResponse(
      FederationRequestStatus status,
      FederationSession federationSession,
      FederatableIdProviderConfiguration federatableIdProviderConfiguration) {
    this.status = status;
    this.federationSession = federationSession;
    this.federatableIdProviderConfiguration = federatableIdProviderConfiguration;
  }

  public FederationRequestStatus status() {
    return status;
  }

  public FederationSession federationSession() {
    return federationSession;
  }

  public FederatableIdProviderConfiguration federatableIdProviderConfiguration() {
    return federatableIdProviderConfiguration;
  }

  public String authorizationRequestUrl() {
    return federationSession.authorizationRequestUri();
  }
}
