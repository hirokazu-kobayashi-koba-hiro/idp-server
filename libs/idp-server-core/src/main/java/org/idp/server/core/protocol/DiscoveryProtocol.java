package org.idp.server.core.protocol;

import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface DiscoveryProtocol {

  ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier);
}
