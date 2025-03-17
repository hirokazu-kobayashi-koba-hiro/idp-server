package org.idp.server.core.protocol;

import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;

public interface DiscoveryProtocol {

  ServerConfigurationRequestResponse getConfiguration(String issuer);
}
