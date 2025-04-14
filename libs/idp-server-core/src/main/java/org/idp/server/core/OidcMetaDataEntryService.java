package org.idp.server.core;

import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.discovery.DiscoveryProtocol;
import org.idp.server.core.discovery.DiscoveryProtocols;
import org.idp.server.core.discovery.OidcMetaDataApi;
import org.idp.server.core.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;

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
