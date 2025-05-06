package org.idp.server.usecases.application;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.core.security.*;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.security.handler.SecurityEventHandler;
import org.idp.server.core.security.repository.SecurityEventCommandRepository;
import org.idp.server.core.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.repository.SecurityEventHookResultCommandRepository;

@Transaction
public class SecurityEventEntryService implements SecurityEventApi {

  SecurityEventHandler securityEventHandler;
  TenantRepository tenantRepository;

  public SecurityEventEntryService(
      SecurityEventHooks securityEventHooks,
      SecurityEventCommandRepository securityEventCommandRepository,
      SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository,
      SecurityEventHookConfigurationQueryRepository hookQueryRepository,
      TenantRepository tenantRepository) {
    this.securityEventHandler =
        new SecurityEventHandler(
            securityEventHooks,
            securityEventCommandRepository,
            securityEventHookResultCommandRepository,
            hookQueryRepository);
    this.tenantRepository = tenantRepository;
  }

  @Override
  public void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    securityEventHandler.handle(tenant, securityEvent);
  }
}
