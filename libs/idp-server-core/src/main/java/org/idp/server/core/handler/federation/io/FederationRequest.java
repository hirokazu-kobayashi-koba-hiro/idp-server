package org.idp.server.core.handler.federation.io;

import java.util.Map;
import org.idp.server.core.tenant.Tenant;

public class FederationRequest {

  Tenant tenant;
  String authorizationRequestId;
  String federatableIdProviderId;
  Map<String, String> customParameters;

  public FederationRequest(
      Tenant tenant, String authorizationRequestId, String federatableIdProviderId) {
    this(tenant, authorizationRequestId, federatableIdProviderId, Map.of());
  }

  public FederationRequest(
      Tenant tenant,
      String authorizationRequestId,
      String federatableIdProviderId,
      Map<String, String> customParameters) {
    this.tenant = tenant;
    this.authorizationRequestId = authorizationRequestId;
    this.federatableIdProviderId = federatableIdProviderId;
    this.customParameters = customParameters;
  }

  public Tenant tenant() {
    return tenant;
  }

  public String authorizationRequestId() {
    return authorizationRequestId;
  }

  public String federatableIdProviderId() {
    return federatableIdProviderId;
  }

  public Map<String, String> customParameters() {
    return customParameters;
  }
}
