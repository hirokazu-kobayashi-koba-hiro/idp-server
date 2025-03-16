package org.idp.server.core.federation;

public class FederationUserinfoRequest {

  String endpoint;
  String accessToken;

  public FederationUserinfoRequest(String endpoint, String accessToken) {
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
