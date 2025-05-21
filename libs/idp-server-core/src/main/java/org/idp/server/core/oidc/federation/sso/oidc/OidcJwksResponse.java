package org.idp.server.core.oidc.federation.sso.oidc;

public class OidcJwksResponse {

  String value;

  public OidcJwksResponse(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
