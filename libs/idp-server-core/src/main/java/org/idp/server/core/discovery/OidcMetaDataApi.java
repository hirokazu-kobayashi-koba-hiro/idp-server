package org.idp.server.core.discovery;

import org.idp.server.core.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface OidcMetaDataApi {

  ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier);

  JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier);
}
