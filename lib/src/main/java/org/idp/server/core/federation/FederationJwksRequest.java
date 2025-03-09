package org.idp.server.core.federation;

public class FederationJwksRequest {
  String endpoint;

  public FederationJwksRequest(String endpoint) {
    this.endpoint = endpoint;
  }

  public String endpoint() {
    return endpoint;
  }
}
