package org.idp.server.authentication.interactors.fidouaf;

import java.util.Map;

public class FIdoUafFacetsResponse {
  int statusCode;
  Map<String, Object> response;

  public FIdoUafFacetsResponse() {}

  public FIdoUafFacetsResponse(int statusCode, Map<String, Object> response) {
    this.statusCode = statusCode;
    this.response = response;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, Object> response() {
    return response;
  }
}
