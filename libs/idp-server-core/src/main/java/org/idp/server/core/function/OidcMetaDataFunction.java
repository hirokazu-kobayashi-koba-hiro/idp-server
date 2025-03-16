package org.idp.server.core.function;

import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface OidcMetaDataFunction {

  ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier);

  JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier);
}
