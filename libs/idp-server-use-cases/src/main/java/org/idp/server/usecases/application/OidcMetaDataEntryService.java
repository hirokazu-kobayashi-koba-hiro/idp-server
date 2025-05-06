package org.idp.server.usecases.application;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.core.oidc.discovery.DiscoveryProtocol;
import org.idp.server.core.oidc.discovery.DiscoveryProtocols;
import org.idp.server.core.oidc.discovery.OidcMetaDataApi;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;

@Transaction
public class OidcMetaDataEntryService implements OidcMetaDataApi {

  TenantRepository tenantRepository;
  DiscoveryProtocols discoveryProtocols;

  public OidcMetaDataEntryService(
      TenantRepository tenantRepository, DiscoveryProtocols discoveryProtocols) {
    this.tenantRepository = tenantRepository;
    this.discoveryProtocols = discoveryProtocols;
  }

  public ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    DiscoveryProtocol discoveryProtocol =
        discoveryProtocols.get(tenant.authorizationProtocolProvider());

    return discoveryProtocol.getConfiguration(tenant);
  }

  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    DiscoveryProtocol discoveryProtocol =
        discoveryProtocols.get(tenant.authorizationProtocolProvider());

    return discoveryProtocol.getJwks(tenant);
  }
}
