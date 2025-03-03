package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.api.DiscoveryApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.discovery.DiscoveryHandler;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestStatus;

@Transactional
public class DiscoveryApiImpl implements DiscoveryApi {

  DiscoveryHandler discoveryHandler;

  DiscoveryApiImpl(DiscoveryHandler discoveryHandler) {
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
