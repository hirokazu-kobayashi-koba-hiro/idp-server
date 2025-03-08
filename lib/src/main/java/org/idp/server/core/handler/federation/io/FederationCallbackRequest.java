package org.idp.server.core.handler.federation.io;

import java.util.Map;
import org.idp.server.core.federation.FederationCallbackParameters;

public class FederationCallbackRequest {

  Map<String, String[]> params;

  public FederationCallbackRequest() {}

  public FederationCallbackRequest(Map<String, String[]> params) {
    this.params = params;
  }

  public FederationCallbackParameters parameters() {
    return new FederationCallbackParameters(params);
  }
}
