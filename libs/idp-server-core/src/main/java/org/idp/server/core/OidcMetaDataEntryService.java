package org.idp.server.core;

import org.idp.server.core.api.OidcMetaDataApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.protocol.DiscoveryProtocol;
import org.idp.server.core.protocol.JwksProtocol;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;

@Transactional
public class OidcMetaDataEntryService implements OidcMetaDataApi {

  TenantRepository tenantRepository;
  DiscoveryProtocol discoveryProtocol;
  JwksProtocol jwksProtocol;

  public OidcMetaDataEntryService(
      TenantRepository tenantRepository,
      DiscoveryProtocol discoveryProtocol,
      JwksProtocol jwksProtocol) {
    this.tenantRepository = tenantRepository;
    this.discoveryProtocol = discoveryProtocol;
    this.jwksProtocol = jwksProtocol;
  }

  public ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return discoveryProtocol.getConfiguration(tenant.issuer());
  }

  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return jwksProtocol.getJwks(tenant.issuer());
  }
}
