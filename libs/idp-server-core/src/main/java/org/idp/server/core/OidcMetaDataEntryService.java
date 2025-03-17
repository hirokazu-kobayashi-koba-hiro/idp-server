package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.OidcMetaDataApi;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.protocol.DiscoveryProtocol;
import org.idp.server.core.protocol.JwksProtocol;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantService;

@Transactional
public class OidcMetaDataEntryService implements OidcMetaDataApi {

  TenantService tenantService;
  DiscoveryProtocol discoveryProtocol;
  JwksProtocol jwksProtocol;

  public OidcMetaDataEntryService(
          TenantService tenantService, DiscoveryProtocol discoveryProtocol, JwksProtocol jwksProtocol) {
    this.tenantService = tenantService;
    this.discoveryProtocol = discoveryProtocol;
    this.jwksProtocol = jwksProtocol;
  }

  public ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);

    return discoveryProtocol.getConfiguration(tenant.issuer());
  }

  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);

    return jwksProtocol.getJwks(tenant.issuer());
  }
}
