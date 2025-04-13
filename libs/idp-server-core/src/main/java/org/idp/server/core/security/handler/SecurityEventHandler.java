package org.idp.server.core.security.handler;

import java.util.logging.Logger;
import org.idp.server.core.security.*;
import org.idp.server.core.security.event.SecurityEventRepository;
import org.idp.server.core.security.hook.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantRepository;

public class SecurityEventHandler {

  SecurityEventRepository securityEventRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;

  Logger log = Logger.getLogger(SecurityEventHandler.class.getName());

  public SecurityEventHandler(
      SecurityEventRepository securityEventRepository,
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository) {
    this.securityEventRepository = securityEventRepository;
    this.securityEventHooks = securityEventHooks;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
  }

  public void handle(Tenant tenant, SecurityEvent securityEvent) {

      securityEventRepository.register(tenant, securityEvent);

    SecurityEventHookConfigurations securityEventHookConfigurations =
        securityEventHookConfigurationQueryRepository.find(tenant);

    securityEventHookConfigurations.forEach(
        hookConfiguration -> {
          SecurityEventHookExecutor securityEventHookExecutor =
              securityEventHooks.get(hookConfiguration.hookType());

          if (securityEventHookExecutor.shouldNotExecute(
              tenant, securityEvent, hookConfiguration)) {
            return;
          }

          log.info(
              String.format(
                  "security event hook execution trigger: %s, type: %s tenant: %s client: %s user: %s, ",
                  securityEvent.type().value(),
                  hookConfiguration.hookType().name(),
                  securityEvent.tenantIdentifierValue(),
                  securityEvent.clientId().value(),
                  securityEvent.user().id()));

          securityEventHookExecutor.execute(tenant, securityEvent, hookConfiguration);
        });
  }
}
