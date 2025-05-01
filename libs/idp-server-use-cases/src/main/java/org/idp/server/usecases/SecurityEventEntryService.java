package org.idp.server.usecases;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.security.*;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.security.event.SecurityEventRepository;
import org.idp.server.core.security.handler.SecurityEventHandler;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;

@Transaction
public class SecurityEventEntryService implements SecurityEventApi {

  SecurityEventHandler securityEventHandler;
  TenantRepository tenantRepository;

  public SecurityEventEntryService(
      SecurityEventRepository securityEventRepository,
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository hookQueryRepository,
      TenantRepository tenantRepository) {
    this.securityEventHandler =
        new SecurityEventHandler(securityEventRepository, securityEventHooks, hookQueryRepository);
    this.tenantRepository = tenantRepository;
  }

  @Override
  public void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    securityEventHandler.handle(tenant, securityEvent);
  }
}
