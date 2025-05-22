package org.idp.server.core.oidc.federation.io;

import java.util.Map;

public class FederationRequestResponse {

  FederationRequestStatus status;
  Map<String, Object> contents;

  public FederationRequestResponse(FederationRequestStatus status) {
    this.status = status;
  }

  public FederationRequestResponse(FederationRequestStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public FederationRequestStatus status() {
    return status;
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
