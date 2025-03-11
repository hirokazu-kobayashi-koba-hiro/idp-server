package org.idp.server.core.federation;

public class FederationJwksResponse {

  String value;

  public FederationJwksResponse(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
