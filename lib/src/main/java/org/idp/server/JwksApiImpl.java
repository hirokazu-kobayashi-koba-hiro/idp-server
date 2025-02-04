package org.idp.server;

import java.util.Map;
import org.idp.server.api.JwksApi;
import org.idp.server.basic.sql.Transactional;
import org.idp.server.handler.discovery.DiscoveryHandler;
import org.idp.server.handler.discovery.io.JwksRequestResponse;
import org.idp.server.handler.discovery.io.JwksRequestStatus;

@Transactional
public class JwksApiImpl implements JwksApi {

  DiscoveryHandler discoveryHandler;

  JwksApiImpl(DiscoveryHandler discoveryHandler) {
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
