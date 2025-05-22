/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.usecases.application.relying_party;

import org.idp.server.core.oidc.discovery.DiscoveryProtocol;
import org.idp.server.core.oidc.discovery.DiscoveryProtocols;
import org.idp.server.core.oidc.discovery.OidcMetaDataApi;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

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
