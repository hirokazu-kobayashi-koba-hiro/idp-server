package org.idp.server.core.api;

import java.util.Map;
import org.idp.server.core.handler.discovery.DiscoveryHandler;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestStatus;
import org.idp.server.core.protcol.DiscoveryApi;

public class DiscoveryApiImpl implements DiscoveryApi {

  DiscoveryHandler discoveryHandler;

  public DiscoveryApiImpl(DiscoveryHandler discoveryHandler) {
    this.discoveryHandler = discoveryHandler;
  }

  public ServerConfigurationRequestResponse getConfiguration(String issuer) {
    try {
      return discoveryHandler.getConfiguration(issuer);
    } catch (Exception exception) {
      return new ServerConfigurationRequestResponse(
          ServerConfigurationRequestStatus.SERVER_ERROR, Map.of());
    }
  }
}
