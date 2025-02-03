package org.idp.server.api;

import org.idp.server.handler.discovery.io.ServerConfigurationRequestResponse;

public interface DiscoveryApi {

 ServerConfigurationRequestResponse getConfiguration(String issuer);
}
