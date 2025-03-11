package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.api.JwksApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.discovery.DiscoveryHandler;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.handler.discovery.io.JwksRequestStatus;

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
