package org.idp.server.core;

import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;

public interface DiscoveryApi {

  ServerConfigurationRequestResponse getConfiguration(String issuer);
}
