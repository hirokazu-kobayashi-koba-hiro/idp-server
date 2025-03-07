package org.idp.server.core.federation;

import java.util.Map;
import java.util.UUID;
import org.idp.server.core.basic.http.QueryParams;

public class FederationAuthorizationRequestCreator {

  FederatableIdProviderConfiguration configuration;
  Map<String, String> customParams;

  public FederationAuthorizationRequestCreator(
      FederatableIdProviderConfiguration federatableIdProviderConfiguration,
      Map<String, String> customParams) {
    this.configuration = federatableIdProviderConfiguration;
    this.customParams = customParams;
  }

  public FederationAuthorizationRequest create() {
    String authorizationEndpoint = configuration.authorizationEndpoint();
    QueryParams queryParams = new QueryParams();
    queryParams.add("client_id", configuration.clientId());
    queryParams.add("redirect_uri", configuration.redirectUri());
    queryParams.add("response_type", "code");
    queryParams.add("state", UUID.randomUUID().toString());
    queryParams.add("nonce", UUID.randomUUID().toString());
    queryParams.add("scope", configuration.scopeAsString());

    return new FederationAuthorizationRequest(
        String.format("%s?%s", authorizationEndpoint, queryParams.params()));
  }
}
