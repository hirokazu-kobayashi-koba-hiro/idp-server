package org.idp.server.core.protocol;

import java.util.Map;
import org.idp.server.core.handler.discovery.DiscoveryHandler;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.handler.discovery.io.JwksRequestStatus;
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
