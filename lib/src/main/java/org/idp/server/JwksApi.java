package org.idp.server;

import java.util.Map;
import org.idp.server.handler.discovery.DiscoveryHandler;
import org.idp.server.handler.discovery.io.JwksRequestResponse;
import org.idp.server.handler.discovery.io.JwksRequestStatus;

public class JwksApi {

  DiscoveryHandler discoveryHandler;

  JwksApi(DiscoveryHandler discoveryHandler) {
    this.discoveryHandler = discoveryHandler;
  }

  public JwksRequestResponse getJwks(String issuer) {
    try {
      return discoveryHandler.getJwks(issuer);
    } catch (Exception exception) {
      return new JwksRequestResponse(JwksRequestStatus.SERVER_ERROR, Map.of());
    }
  }
}
