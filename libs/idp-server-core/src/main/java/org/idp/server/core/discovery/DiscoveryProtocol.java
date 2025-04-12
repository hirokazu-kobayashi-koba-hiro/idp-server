package org.idp.server.core.discovery;

import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface DiscoveryProtocol {

  ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier);
}
