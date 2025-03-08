package org.idp.server.core.handler.federation.io;

import java.util.Map;
import org.idp.server.core.type.oauth.TokenIssuer;

public class FederationRequest {

  String federatableIdProviderId;
  String issuer;
  Map<String, String> customParameters;

  public FederationRequest(String issuer, String federatableIdProviderId) {
    this(issuer, federatableIdProviderId, Map.of());
  }

  public FederationRequest(
      String issuer, String federatableIdProviderId, Map<String, String> customParameters) {
    this.issuer = issuer;
    this.federatableIdProviderId = federatableIdProviderId;
    this.customParameters = customParameters;
  }

  public String federatableIdProviderId() {
    return federatableIdProviderId;
  }

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public Map<String, String> customParameters() {
    return customParameters;
  }
}
