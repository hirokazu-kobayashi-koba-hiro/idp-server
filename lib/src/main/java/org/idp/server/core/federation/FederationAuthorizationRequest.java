package org.idp.server.core.federation;

public class FederationAuthorizationRequest {
  String url;

  public FederationAuthorizationRequest() {}

  public FederationAuthorizationRequest(String url) {
    this.url = url;
  }

  public String url() {
    return url;
  }
}
