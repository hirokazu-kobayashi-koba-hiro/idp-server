package org.idp.server.core.federation;

import java.util.UUID;
import org.idp.server.core.basic.http.QueryParams;
import org.idp.server.core.handler.federation.io.FederationRequest;

public class FederationSessionCreator {

  FederatableIdProviderConfiguration configuration;
  FederationRequest federationRequest;

  public FederationSessionCreator(
      FederatableIdProviderConfiguration federatableIdProviderConfiguration,
      FederationRequest federationRequest) {
    this.configuration = federatableIdProviderConfiguration;
    this.federationRequest = federationRequest;
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
        federationRequest.authorizationRequestId(),
        federationRequest.tokenIssuerValue(),
        state,
        nonce,
        configuration.identifier(),
        configuration.clientId(),
        configuration.redirectUri(),
        authorizationRequestUri);
  }
}
