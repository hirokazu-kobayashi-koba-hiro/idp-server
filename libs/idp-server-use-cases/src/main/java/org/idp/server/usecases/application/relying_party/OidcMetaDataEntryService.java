package org.idp.server.usecases.application.relying_party;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.oidc.discovery.DiscoveryProtocol;
import org.idp.server.core.oidc.discovery.DiscoveryProtocols;
import org.idp.server.core.oidc.discovery.OidcMetaDataApi;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.platform.datasource.Transaction;

@Transaction(readOnly = true)
public class OidcMetaDataEntryService implements OidcMetaDataApi {

  TenantQueryRepository tenantQueryRepository;
  DiscoveryProtocols discoveryProtocols;

  public OidcMetaDataEntryService(
      TenantQueryRepository tenantQueryRepository, DiscoveryProtocols discoveryProtocols) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.discoveryProtocols = discoveryProtocols;
  }

  public ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    DiscoveryProtocol discoveryProtocol = discoveryProtocols.get(tenant.authorizationProvider());

    return discoveryProtocol.getConfiguration(tenant);
  }

  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    DiscoveryProtocol discoveryProtocol = discoveryProtocols.get(tenant.authorizationProvider());

    return discoveryProtocol.getJwks(tenant);
  }
}
