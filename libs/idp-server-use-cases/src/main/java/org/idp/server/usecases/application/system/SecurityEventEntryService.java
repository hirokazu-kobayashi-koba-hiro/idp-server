package org.idp.server.usecases.application.system;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.security.*;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.security.handler.SecurityEventHandler;
import org.idp.server.core.security.repository.SecurityEventCommandRepository;
import org.idp.server.core.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.repository.SecurityEventHookResultCommandRepository;

@Transaction
public class SecurityEventEntryService implements SecurityEventApi {

  SecurityEventHandler securityEventHandler;
  TenantQueryRepository tenantQueryRepository;

  public SecurityEventEntryService(
      SecurityEventHooks securityEventHooks,
      SecurityEventCommandRepository securityEventCommandRepository,
      SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository,
      SecurityEventHookConfigurationQueryRepository hookQueryRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.securityEventHandler =
        new SecurityEventHandler(
            securityEventHooks,
            securityEventCommandRepository,
            securityEventHookResultCommandRepository,
            hookQueryRepository);
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    securityEventHandler.handle(tenant, securityEvent);
  }
}
