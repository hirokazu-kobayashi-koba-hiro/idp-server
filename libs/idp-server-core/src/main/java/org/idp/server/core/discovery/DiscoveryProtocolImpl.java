package org.idp.server.core.discovery;

import java.util.Map;
import org.idp.server.core.discovery.handler.DiscoveryHandler;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestStatus;
import org.idp.server.core.tenant.TenantIdentifier;

public class DiscoveryProtocolImpl implements DiscoveryProtocol {

  DiscoveryHandler discoveryHandler;

  public DiscoveryProtocolImpl(DiscoveryHandler discoveryHandler) {
    this.discoveryHandler = discoveryHandler;
  }

  public ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier) {
    try {
      return discoveryHandler.getConfiguration(tenantIdentifier);
    } catch (Exception exception) {
      return new ServerConfigurationRequestResponse(
          ServerConfigurationRequestStatus.SERVER_ERROR, Map.of());
    }
  }
}
