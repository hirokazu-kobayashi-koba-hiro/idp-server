package org.idp.server.core.federation.oidc;

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
