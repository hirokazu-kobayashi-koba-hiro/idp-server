package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.discovery.DiscoveryProtocol;
import org.idp.server.core.discovery.JwksProtocol;
import org.idp.server.core.discovery.OidcMetaDataApi;
import org.idp.server.core.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestResponse;
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

    return discoveryProtocol.getConfiguration(tenantIdentifier);
  }

  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {

    return jwksProtocol.getJwks(tenantIdentifier);
  }
}
