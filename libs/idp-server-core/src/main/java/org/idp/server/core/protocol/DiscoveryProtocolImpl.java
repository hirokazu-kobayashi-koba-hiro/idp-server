package org.idp.server.core.protocol;

import java.util.Map;
import org.idp.server.core.handler.discovery.DiscoveryHandler;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestStatus;
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
