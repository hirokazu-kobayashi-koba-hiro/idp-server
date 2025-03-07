package org.idp.server.core.handler.federation.io;

import java.util.Map;

public class FederationRequest {

  String federatableIdProviderId;
  Map<String, String> customParameters;

  public FederationRequest(String federatableIdProviderId) {
    this(federatableIdProviderId, Map.of());
  }

  public FederationRequest(String federatableIdProviderId, Map<String, String> customParameters) {
    this.federatableIdProviderId = federatableIdProviderId;
    this.customParameters = customParameters;
  }

  public String getFederatableIdProviderId() {
    return federatableIdProviderId;
  }

  public Map<String, String> customParameters() {
    return customParameters;
  }
}
