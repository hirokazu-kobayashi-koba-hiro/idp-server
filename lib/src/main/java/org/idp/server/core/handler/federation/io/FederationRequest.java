package org.idp.server.core.handler.federation.io;

import java.util.Map;
import org.idp.server.core.type.oauth.TokenIssuer;

public class FederationRequest {

  String authorizationRequestId;
  String federatableIdProviderId;
  String issuer;
  Map<String, String> customParameters;

  public FederationRequest(
      String authorizationRequestId, String issuer, String federatableIdProviderId) {
    this(authorizationRequestId, issuer, federatableIdProviderId, Map.of());
  }

  public FederationRequest(
      String authorizationRequestId,
      String issuer,
      String federatableIdProviderId,
      Map<String, String> customParameters) {
    this.authorizationRequestId = authorizationRequestId;
    this.issuer = issuer;
    this.federatableIdProviderId = federatableIdProviderId;
    this.customParameters = customParameters;
  }

  public String authorizationRequestId() {
    return authorizationRequestId;
  }

  public String federatableIdProviderId() {
    return federatableIdProviderId;
  }

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public String tokenIssuerValue() {
    return issuer;
  }

  public Map<String, String> customParameters() {
    return customParameters;
  }
}
