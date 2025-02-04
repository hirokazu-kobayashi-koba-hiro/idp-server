package org.idp.server;

import java.util.Map;
import org.idp.server.api.DiscoveryApi;
import org.idp.server.basic.sql.Transactional;
import org.idp.server.handler.discovery.DiscoveryHandler;
import org.idp.server.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.handler.discovery.io.ServerConfigurationRequestStatus;

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
