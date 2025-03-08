package org.idp.server.core.federation;

import java.util.Map;
import java.util.UUID;
import org.idp.server.core.basic.http.QueryParams;

public class FederationSessionCreator {

  FederatableIdProviderConfiguration configuration;
  Map<String, String> customParams;

  public FederationSessionCreator(
      FederatableIdProviderConfiguration federatableIdProviderConfiguration,
      Map<String, String> customParams) {
    this.configuration = federatableIdProviderConfiguration;
    this.customParams = customParams;
  }

  public FederationSession create() {
    String authorizationEndpoint = configuration.authorizationEndpoint();
    String state = UUID.randomUUID().toString();
    String nonce = UUID.randomUUID().toString();

    QueryParams queryParams = new QueryParams();
    queryParams.add("client_id", configuration.clientId());
    queryParams.add("redirect_uri", configuration.redirectUri());
    queryParams.add("response_type", "code");
    queryParams.add("state", state);
    queryParams.add("nonce", nonce);
    queryParams.add("scope", configuration.scopeAsString());

    String authorizationRequestUri =
        String.format("%s?%s", authorizationEndpoint, queryParams.params());

    return new FederationSession(
        state,
        nonce,
        configuration.identifier(),
        configuration.clientId(),
        configuration.redirectUri(),
        authorizationRequestUri);
  }
}
