package org.idp.server.core.federation.sso.oidc;

public class OidcJwksRequest {
  String endpoint;

  public OidcJwksRequest(String endpoint) {
    this.endpoint = endpoint;
  }

  public String endpoint() {
    return endpoint;
  }
}
