package org.idp.sample.application.service;

import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.DiscoveryApi;
import org.idp.server.api.JwksApi;
import org.idp.server.handler.discovery.io.JwksRequestResponse;
import org.idp.server.handler.discovery.io.ServerConfigurationRequestResponse;
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
