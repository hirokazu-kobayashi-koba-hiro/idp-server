package org.idp.server.core.discovery;

import java.util.Map;
import org.idp.server.core.discovery.handler.DiscoveryHandler;
import org.idp.server.core.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.discovery.handler.io.JwksRequestStatus;
import org.idp.server.core.tenant.TenantIdentifier;

public class JwksProtocolImpl implements JwksProtocol {

  DiscoveryHandler discoveryHandler;

  public JwksProtocolImpl(DiscoveryHandler discoveryHandler) {
    this.discoveryHandler = discoveryHandler;
  }

  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {
    try {
      return discoveryHandler.getJwks(tenantIdentifier);
    } catch (Exception exception) {
      return new JwksRequestResponse(JwksRequestStatus.SERVER_ERROR, Map.of());
    }
  }
}
