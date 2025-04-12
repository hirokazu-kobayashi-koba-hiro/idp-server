package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.security.*;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.security.event.SecurityEventRepository;
import org.idp.server.core.security.handler.SecurityEventHandler;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.tenant.TenantRepository;

@Transactional
public class SecurityEventEntryService implements SecurityEventApi {

  SecurityEventHandler securityEventHandler;

  public SecurityEventEntryService(
      TenantRepository tenantRepository,
      SecurityEventRepository securityEventRepository,
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository hookQueryRepository) {
    this.securityEventHandler =
        new SecurityEventHandler(
            tenantRepository, securityEventRepository, securityEventHooks, hookQueryRepository);
  }

  @Override
  public void handle(SecurityEvent securityEvent) {
    try {

      securityEventHandler.handle(securityEvent);
    } catch (Exception e) {

      e.printStackTrace();
    }
  }
}
