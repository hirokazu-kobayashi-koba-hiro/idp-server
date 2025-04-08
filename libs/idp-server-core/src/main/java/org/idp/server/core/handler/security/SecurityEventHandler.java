package org.idp.server.core.handler.security;

import java.util.logging.Logger;
import org.idp.server.core.security.*;
import org.idp.server.core.security.event.SecurityEventRepository;
import org.idp.server.core.security.hook.*;
import org.idp.server.core.security.hook.ssf.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantRepository;

public class SecurityEventHandler {

  TenantRepository tenantRepository;
  SecurityEventRepository securityEventRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;

  Logger log = Logger.getLogger(SecurityEventHandler.class.getName());

  public SecurityEventHandler(
      TenantRepository tenantRepository,
      SecurityEventRepository securityEventRepository,
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository) {
    this.tenantRepository = tenantRepository;
    this.securityEventRepository = securityEventRepository;
    this.securityEventHooks = securityEventHooks;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
  }

  public void handle(SecurityEvent securityEvent) {

    securityEventRepository.register(securityEvent);

    Tenant tenant = tenantRepository.get(securityEvent.tenantIdentifier());

    SecurityEventHookConfigurations securityEventHookConfigurations =
        securityEventHookConfigurationQueryRepository.find(tenant, securityEvent.type());

    securityEventHookConfigurations.forEach(
        hookConfiguration -> {
          log.info(
              String.format(
                  "security event hook execution trigger: %s, type: %s tenant: %s client: %s user: %s, ",
                  hookConfiguration.triggerType().name(),
                  hookConfiguration.hookType().name(),
                  securityEvent.tenantIdentifierValue(),
                  securityEvent.clientId().value(),
                  securityEvent.user().id()));

          SecurityEventHookExecutor securityEventHookExecutor =
              securityEventHooks.get(hookConfiguration.hookType());

          securityEventHookExecutor.execute(tenant, securityEvent, hookConfiguration);
        });
  }
}
