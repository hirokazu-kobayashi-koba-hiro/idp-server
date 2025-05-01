package org.idp.server.core.oidc.discovery;

import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface OidcMetaDataApi {

  ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier);

  JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier);
}
