package org.idp.server.application.service;

import org.idp.server.application.service.tenant.TenantService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.DiscoveryApi;
import org.idp.server.core.api.JwksApi;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.springframework.stereotype.Service;

@Service
public class OidcMetaDataService {

  TenantService tenantService;
  DiscoveryApi discoveryApi;
  JwksApi jwksApi;

  public OidcMetaDataService(
      TenantService tenantService, IdpServerApplication idpServerApplication) {
    this.tenantService = tenantService;
    this.discoveryApi = idpServerApplication.discoveryApi();
    this.jwksApi = idpServerApplication.jwksApi();
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
