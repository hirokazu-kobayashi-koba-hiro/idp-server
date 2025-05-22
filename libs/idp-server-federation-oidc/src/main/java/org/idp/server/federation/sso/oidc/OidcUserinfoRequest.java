package org.idp.server.federation.sso.oidc;

public class OidcUserinfoRequest {

  String endpoint;
  String accessToken;

  public OidcUserinfoRequest(String endpoint, String accessToken) {
    this.endpoint = endpoint;
    this.accessToken = accessToken;
  }

  public String endpoint() {
    return endpoint;
  }

  public String accessToken() {
    return accessToken;
  }
}
