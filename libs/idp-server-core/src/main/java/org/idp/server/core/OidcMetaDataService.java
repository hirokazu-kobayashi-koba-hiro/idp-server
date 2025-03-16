package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.function.OidcMetaDataFunction;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.protcol.DiscoveryApi;
import org.idp.server.core.protcol.JwksApi;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantService;

@Transactional
public class OidcMetaDataService implements OidcMetaDataFunction {

  TenantService tenantService;
  DiscoveryApi discoveryApi;
  JwksApi jwksApi;

  public OidcMetaDataService(
      TenantService tenantService, DiscoveryApi discoveryApi, JwksApi jwksApi) {
    this.tenantService = tenantService;
    this.discoveryApi = discoveryApi;
    this.jwksApi = jwksApi;
  }

  public ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);

    return discoveryApi.getConfiguration(tenant.issuer());
  }

  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);

    return jwksApi.getJwks(tenant.issuer());
  }
}
